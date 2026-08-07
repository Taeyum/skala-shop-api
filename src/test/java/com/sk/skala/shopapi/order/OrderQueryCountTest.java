package com.sk.skala.shopapi.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.sk.skala.shopapi.customer.entity.Customer;
import com.sk.skala.shopapi.customer.repository.CustomerRepository;
import com.sk.skala.shopapi.global.exception.ResponseException;
import com.sk.skala.shopapi.order.dto.OrderListDto;
import com.sk.skala.shopapi.order.dto.OrderRequest;
import com.sk.skala.shopapi.order.repository.OrderItemRepository;
import com.sk.skala.shopapi.order.service.OrderService;
import com.sk.skala.shopapi.product.entity.Product;
import com.sk.skala.shopapi.product.repository.ProductRepository;
import com.sk.skala.shopapi.support.PostgresTestContainer;

import jakarta.persistence.EntityManagerFactory;

/**
 * N+1 회귀 방지. 로그는 사람이 보고 잊지만 테스트는 깨진다.
 * <p>
 * <b>이 테스트에는 {@code @Transactional}을 붙이지 않는다.</b> 붙이면 픽스처를 만든
 * 영속성 컨텍스트가 조회까지 이어져 {@code Product}가 <b>1차 캐시에서 해결된다</b> —
 * 쿼리가 나가지 않으니 N+1이 있어도 "쿼리 2개"로 통과한다. 실제 HTTP 요청은 매번 새 컨텍스트에서
 * 시작하므로, 트랜잭션을 나누지 않는 편이 운영 동작과도 일치한다 (PLAN.md Phase 3 주의 ①).
 * <p>
 * <b>측정 순서를 지킨다</b> — 쿼리 수보다 <b>반환된 행 수를 먼저</b> 단언한다.
 * 조회가 아무 일도 하지 않으면 쿼리도 나가지 않아 "완벽히 개선됨"으로 보인다 (주의 ②).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainer.class)
class OrderQueryCountTest {

	/** 1차 캐시에 기대지 않으려면 상품이 서로 달라야 한다. 같은 상품 반복으로는 N+1이 재현되지 않는다 */
	private static final int PRODUCT_KINDS = 20;

	/** 고객 1 + 주문목록(상품 조인) 1. 상품 종수와 무관하게 상수여야 한다 */
	private static final int EXPECTED_QUERIES = 2;

	@Autowired private OrderService orderService;
	@Autowired private CustomerRepository customerRepository;
	@Autowired private ProductRepository productRepository;
	@Autowired private OrderItemRepository orderItemRepository;
	@Autowired private EntityManagerFactory entityManagerFactory;

	private String customerId;

	@BeforeEach
	void 픽스처_준비() {
		customerId = "qc-" + System.nanoTime();
		customerRepository.save(Customer.register(customerId, "{noop}pw", new BigDecimal("1000000.00")));

		List<Product> products = new ArrayList<>();
		for (int i = 0; i < PRODUCT_KINDS; i++) {
			products.add(productRepository.save(
					Product.of("qc-product-" + System.nanoTime() + "-" + i, new BigDecimal("1000.00"))));
		}
		for (Product product : products) {
			orderService.placeOrder(customerId, orderRequest(product.getId(), 1));
		}
	}

	/** OrderRequest는 Jackson 역직렬화용이라 생성자가 없다. 테스트 편의로 운영 DTO를 고치지 않는다 */
	private static OrderRequest orderRequest(Long productId, int quantity) {
		OrderRequest request = new OrderRequest();
		request.setProductId(productId);
		request.setQuantity(quantity);
		return request;
	}

	@AfterEach
	void 정리() {
		// 테스트는 자기 데이터를 직접 치운다. 남기면 다음 테스트의 건수 단언이 깨진다
		customerRepository.findByCustomerId(customerId).ifPresent(customer -> {
			orderItemRepository.deleteAll(orderItemRepository.findByCustomer(customer));
			customerRepository.delete(customer);
		});
	}

	private Statistics statistics() {
		return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
	}

	@Test
	@DisplayName("주문 조회는 상품 종수와 무관하게 쿼리 2개다")
	void 주문_조회는_상품_종수와_무관하게_쿼리_2개다() {
		// given — 계측을 0으로 맞춘다. 픽스처가 낸 쿼리가 섞이면 수치가 무의미하다
		Statistics statistics = statistics();
		statistics.clear();

		// when
		OrderListDto result = orderService.getCustomerOrders(customerId, customerId);

		// then — ★ 쿼리 수보다 먼저, 조회가 실제로 일했는지 확인한다
		assertThat(result.getProducts())
				.as("반환 행 수. 여기가 비면 쿼리도 안 나가 '개선됨'으로 보인다")
				.hasSize(PRODUCT_KINDS);
		assertThat(result.getProducts())
				.as("연관 상품이 실제로 로딩됐는가 — 프록시만 들고 있으면 이름이 비어 있다")
				.allSatisfy(item -> assertThat(item.getProductName()).isNotBlank());

		// 행 수가 확인된 뒤에야 쿼리 수가 의미를 가진다.
		// @EntityGraph를 지우면 이 단언이 22로 깨진다 (docs/evidence/n-plus-1.md)
		assertThat(statistics.getPrepareStatementCount())
				.as("상품 %d종 조회에 나간 쿼리 수", PRODUCT_KINDS)
				.isEqualTo(EXPECTED_QUERIES);
	}

	@Test
	@DisplayName("측정 장치 검증 — 조회가 실패하면 쿼리 수는 근거가 되지 못한다")
	void 소유자가_아니면_조회가_아예_수행되지_않는다() {
		// given
		Statistics statistics = statistics();
		statistics.clear();

		// when — 남의 리소스 조회 (HTTP 경로에서 쿠키 없이 호출하면 401로 튕기는 것과 같은 자리)
		assertThatThrownBy(() -> orderService.getCustomerOrders("someone-else", customerId))
				.isInstanceOf(ResponseException.class);

		// then — 쿼리는 거의 나가지 않는다.
		// 이 수치만 보면 "완벽히 개선됨"이지만 조회는 한 번도 일어나지 않았다.
		// 위 테스트가 행 수를 먼저 단언하는 이유가 이것이다
		assertThat(statistics.getPrepareStatementCount())
				.as("인가 실패 시 쿼리 수 — 개선의 근거로 쓸 수 없는 값")
				.isLessThan(EXPECTED_QUERIES);
	}
}
