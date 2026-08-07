package com.sk.skala.shopapi.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import com.sk.skala.shopapi.customer.entity.Customer;
import com.sk.skala.shopapi.customer.repository.CustomerRepository;
import com.sk.skala.shopapi.order.dto.OrderRequest;
import com.sk.skala.shopapi.order.repository.OrderItemRepository;
import com.sk.skala.shopapi.order.service.OrderService;
import com.sk.skala.shopapi.product.entity.Product;
import com.sk.skala.shopapi.product.repository.ProductRepository;
import com.sk.skala.shopapi.support.PessimisticOrderService;
import com.sk.skala.shopapi.support.PessimisticOrderService.PessimisticOrderer;
import com.sk.skala.shopapi.support.PostgresTestContainer;

/**
 * 낙관적 락 vs 비관적 락 — 같은 시나리오를 락 방식만 바꿔 돌린다.
 * <p>
 * "충돌이 잦으면 비관적, 드물면 낙관적"이라는 트레이드오프를 <b>수치로</b> 확인하는 것이 목적이다.
 * 두 실행은 <b>같은 픽스처·같은 스레드 수·같은 배리어</b>를 쓴다 — 하나라도 다르면 비교가 아니다.
 * <p>
 * 이 시나리오는 <b>경합이 극단적으로 높은 쪽</b>이다(100개 요청이 같은 행 하나를 갱신).
 * 실제 트래픽에서 이 정도로 한 행에 몰리는 경우는 드물다는 점을 함께 읽어야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({ PostgresTestContainer.class, PessimisticOrderService.class })
class LockComparisonTest {

	private static final int THREADS = 100;
	private static final BigDecimal INITIAL_POINT = new BigDecimal("1000000.00");
	private static final BigDecimal UNIT_PRICE = new BigDecimal("1000.00");
	/** 재시도 상한. 무한 재시도는 장애 시 스레드를 전부 묶어 더 큰 문제가 된다 */
	private static final int MAX_RETRY = 50;

	@Autowired private OrderService orderService;
	@Autowired private PessimisticOrderer pessimisticOrderer;
	@Autowired private CustomerRepository customerRepository;
	@Autowired private ProductRepository productRepository;
	@Autowired private OrderItemRepository orderItemRepository;

	private String customerId;
	private final List<Long> productIds = new ArrayList<>();

	@BeforeEach
	void 픽스처_준비() {
		customerId = "lc-" + System.nanoTime();
		customerRepository.save(Customer.register(customerId, "{noop}pw", INITIAL_POINT));
		productIds.clear();
		for (int i = 0; i < THREADS; i++) {
			productIds.add(productRepository.save(
					Product.of("lc-product-" + System.nanoTime() + "-" + i, UNIT_PRICE)).getId());
		}
	}

	@AfterEach
	void 정리() {
		customerRepository.findByCustomerId(customerId).ifPresent(customer -> {
			orderItemRepository.deleteAll(orderItemRepository.findByCustomer(customer));
			customerRepository.delete(customer);
		});
	}

	@Test
	@DisplayName("낙관적 락 — 동시 주문 100건")
	void 낙관적_락() throws Exception {
		measure("낙관적(@Version)", (cid, productId) ->
				orderService.placeOrder(cid, orderRequest(productId, 1)));
	}

	@Test
	@DisplayName("비관적 락 — 동시 주문 100건")
	void 비관적_락() throws Exception {
		measure("비관적(SELECT FOR UPDATE)", (cid, productId) ->
				pessimisticOrderer.placeOrder(cid, productId, 1));
	}

	@Test
	@DisplayName("낙관적 락 + 재시도 — 동시 주문 100건을 전부 완료할 때까지")
	void 낙관적_락_재시도() throws Exception {
		// 낙관적 락의 실패 86건은 '처리하지 않아도 되는 요청'이 아니라 '다시 해야 하는 요청'이다.
		// 실패율만 보면 낙관적이 나빠 보이고 TPS만 보면 좋아 보인다.
		// 같은 일(100건)을 끝내는 데 걸리는 시간으로 맞춰야 비교가 된다
		measure("낙관적+재시도", (cid, productId) -> {
			for (int attempt = 1; ; attempt++) {
				try {
					orderService.placeOrder(cid, orderRequest(productId, 1));
					return;
				} catch (OptimisticLockingFailureException e) {
					if (attempt >= MAX_RETRY) {
						throw e;
					}
				}
			}
		});
	}

	/** 두 방식이 정확히 같은 절차를 거치도록 측정 로직을 공유한다 */
	private void measure(String label, BiConsumer<String, Long> order) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(THREADS);
		CountDownLatch ready = new CountDownLatch(THREADS);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(THREADS);
		AtomicInteger success = new AtomicInteger();
		ConcurrentHashMap<String, AtomicInteger> failures = new ConcurrentHashMap<>();

		for (Long productId : productIds) {
			executor.submit(() -> {
				ready.countDown();
				try {
					start.await();
					order.accept(customerId, productId);
					success.incrementAndGet();
				} catch (Exception e) {
					failures.computeIfAbsent(e.getClass().getSimpleName(), k -> new AtomicInteger())
							.incrementAndGet();
				} finally {
					done.countDown();
				}
			});
		}
		assertThat(ready.await(30, TimeUnit.SECONDS))
				.as("전원이 출발선에 도달했는가 — 아니면 아래 수치는 동시 실행의 결과가 아니다")
				.isTrue();

		// 계측 구간은 배리어를 푼 시점부터다. 픽스처 생성 시간이 섞이면 처리량이 왜곡된다
		long began = System.nanoTime();
		start.countDown();
		assertThat(done.await(120, TimeUnit.SECONDS)).as("전원이 끝났는가").isTrue();
		long elapsedMs = Math.max(1, (System.nanoTime() - began) / 1_000_000);
		executor.shutdown();

		BigDecimal finalPoint = customerRepository.findByCustomerId(customerId).orElseThrow()
				.getCustomerPoint();
		BigDecimal expected = INITIAL_POINT.subtract(UNIT_PRICE.multiply(BigDecimal.valueOf(success.get())));

		System.out.printf("%n[락비교] %-26s 성공 %3d · 실패 %3d %s · %4dms · 성공TPS %.0f%n",
				label, success.get(), THREADS - success.get(), failures, elapsedMs,
				success.get() * 1000.0 / elapsedMs);
		System.out.printf("[락비교] %-26s 최종 %s / 기대 %s%n%n",
				label, finalPoint.toPlainString(), expected.toPlainString());

		// 어느 방식이든 정합성은 지켜져야 한다. 처리량 비교는 그 다음 이야기다
		assertThat(finalPoint)
				.as("%s — 성공 건수만큼만 차감됐는가", label)
				.usingComparator(BigDecimal::compareTo)
				.isEqualTo(expected);
	}

	private static OrderRequest orderRequest(Long productId, int quantity) {
		OrderRequest request = new OrderRequest();
		request.setProductId(productId);
		request.setQuantity(quantity);
		return request;
	}
}
