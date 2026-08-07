package com.sk.skala.shopapi.order.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.sk.skala.shopapi.customer.entity.Customer;
import com.sk.skala.shopapi.customer.repository.CustomerRepository;
import com.sk.skala.shopapi.order.entity.OrderItem;
import com.sk.skala.shopapi.product.entity.Product;
import com.sk.skala.shopapi.product.repository.ProductRepository;
import com.sk.skala.shopapi.global.config.JpaAuditingConfig;
import com.sk.skala.shopapi.support.PostgresTestContainer;

import jakarta.persistence.EntityManager;

/**
 * <b>운영과 같은 PostgreSQL에서 돈다.</b> 임베디드 DB로 바꾸면 방언 차이를 못 잡는다 —
 * 복합 UNIQUE 위반이 어떤 예외로 번역되는지, 조인이 어떻게 나가는지가 엔진마다 다르다.
 * <p>
 * {@code replace = NONE}이 필요한 이유 — {@code @DataJpaTest}는 기본적으로 데이터소스를
 * 임베디드로 바꾸려 한다. Testcontainers를 쓰는 의미가 사라지므로 대체를 끈다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
// JpaAuditingConfig를 함께 담는다 — @DataJpaTest 슬라이스는 @EnableJpaAuditing을
// 자동으로 포함하지 않아, 없으면 created_at이 null인 채 저장되어 NOT NULL 제약에 걸린다
@Import({ PostgresTestContainer.class, JpaAuditingConfig.class })
class OrderItemRepositoryTest {

	@Autowired private OrderItemRepository orderItemRepository;
	@Autowired private CustomerRepository customerRepository;
	@Autowired private ProductRepository productRepository;
	@Autowired private EntityManager entityManager;

	private Customer customer;

	@BeforeEach
	void setUp() {
		customer = customerRepository.save(
				Customer.register("repo-" + System.nanoTime(), "pw", new BigDecimal("1000000.00")));
	}

	private Product product(String suffix) {
		return productRepository.save(
				Product.of("repo-product-" + System.nanoTime() + suffix, new BigDecimal("1000.00")));
	}

	@Test
	void 고객의_보유_상품을_조회한다() {
		orderItemRepository.save(OrderItem.of(customer, product("a"), 2, new BigDecimal("2000.00")));
		orderItemRepository.save(OrderItem.of(customer, product("b"), 1, new BigDecimal("1000.00")));
		entityManager.flush();
		entityManager.clear();

		List<OrderItem> found = orderItemRepository.findByCustomer(customer);

		assertThat(found).hasSize(2);
	}

	@Test
	void findByCustomer는_상품을_함께_가져온다_N_plus_1_방지() {
		for (int i = 0; i < 5; i++) {
			orderItemRepository.save(OrderItem.of(customer, product("n" + i), 1, new BigDecimal("1000.00")));
		}
		entityManager.flush();
		// ★ 1차 캐시를 비우지 않으면 Product가 캐시에서 해결돼 N+1이 있어도 쿼리가 안 나간다
		entityManager.clear();

		Statistics statistics = entityManager.getEntityManagerFactory()
				.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		List<OrderItem> found = orderItemRepository.findByCustomer(customer);
		// 연관을 실제로 건드려야 지연 로딩이 발동한다. 건드리지 않으면 프록시만 들고 통과한다
		found.forEach(item -> item.getProduct().getProductName());

		// 행 수를 먼저 단언한다 — 비어 있으면 쿼리도 안 나가 '개선됨'으로 보인다
		assertThat(found).hasSize(5);
		assertThat(statistics.getPrepareStatementCount())
				.as("@EntityGraph가 상품을 조인으로 함께 가져오므로 1개다")
				.isEqualTo(1);
	}

	@Test
	void 같은_고객_같은_상품은_한_행만_허용한다() {
		// 재주문 시 수량 누적이라는 규칙을 DB 제약으로 못 박은 것.
		// 애플리케이션 로직이 뚫려도 이 제약이 마지막 방어선이다
		Product product = product("dup");
		orderItemRepository.save(OrderItem.of(customer, product, 1, new BigDecimal("1000.00")));
		entityManager.flush();

		// flush가 아니라 save에서 터진다 — IDENTITY 전략은 INSERT를 지연할 수 없다
		assertThatThrownBy(() ->
				orderItemRepository.save(OrderItem.of(customer, product, 1, new BigDecimal("1000.00"))))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 다른_고객이면_같은_상품이라도_각자_행을_갖는다() {
		Customer other = customerRepository.save(
				Customer.register("repo-other-" + System.nanoTime(), "pw", new BigDecimal("1000000.00")));
		Product product = product("shared");
		orderItemRepository.save(OrderItem.of(customer, product, 1, new BigDecimal("1000.00")));
		orderItemRepository.save(OrderItem.of(other, product, 1, new BigDecimal("1000.00")));

		entityManager.flush();

		assertThat(orderItemRepository.findByCustomer(customer)).hasSize(1);
		assertThat(orderItemRepository.findByCustomer(other)).hasSize(1);
	}

	@Test
	void 금액은_scale_2로_저장된다() {
		// precision=19, scale=2. 저장·조회를 왕복해도 값이 보존되는지
		orderItemRepository.save(OrderItem.of(customer, product("s"), 3, new BigDecimal("12345.67")));
		entityManager.flush();
		entityManager.clear();

		assertThat(orderItemRepository.findByCustomer(customer).get(0).getOrderedAmount())
				.usingComparator(BigDecimal::compareTo)
				.isEqualTo(new BigDecimal("12345.67"));
	}
}
