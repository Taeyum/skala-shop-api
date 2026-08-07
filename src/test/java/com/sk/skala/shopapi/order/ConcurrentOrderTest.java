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
import com.sk.skala.shopapi.order.dto.OrderRequest;
import com.sk.skala.shopapi.order.repository.OrderItemRepository;
import com.sk.skala.shopapi.order.service.OrderService;
import com.sk.skala.shopapi.product.entity.Product;
import com.sk.skala.shopapi.product.repository.ProductRepository;
import com.sk.skala.shopapi.support.PostgresTestContainer;

/**
 * 동일 계정 동시 주문의 포인트 정합성.
 * <p>
 * <b>{@code @Transactional}을 붙이지 않는다.</b> 붙이면 모든 스레드가 테스트의 트랜잭션 하나를
 * 공유해 동시성 자체가 사라진다 — 검증이 통과해도 아무것도 증명하지 못한다
 * ({@code .claude/rules/testing.md}).
 * <p>
 * 스레드마다 <b>서로 다른 상품</b>을 주문한다. 같은 상품이면 {@code (customer_id, product_id)}
 * 복합 UNIQUE에 걸려 <b>제약 위반이 먼저 터지고</b>, 정작 보려는 <b>고객 포인트의 경합</b>이
 * 그 뒤에 가려진다. 경합 지점을 하나만 남긴다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainer.class)
class ConcurrentOrderTest {

	private static final int THREADS = 100;
	/**
	 * <b>풀 크기는 스레드 수와 같아야 한다.</b> 작게 잡으면 뒤쪽 작업이 큐에서 대기하는데,
	 * 먼저 실행된 스레드는 {@code ready}가 0이 되기를 기다리고 있어 <b>배리어가 스스로 교착</b>한다.
	 * 실제로 풀 32 / 스레드 100으로 두었다가 {@code ready.await(30초)} 타임아웃이 만료될 때까지
	 * 아무 일도 일어나지 않았고, 그 30초가 그대로 "소요 시간"으로 찍혔다 (JOURNAL 2026-08-07).
	 */
	private static final int POOL = THREADS;
	private static final BigDecimal INITIAL_POINT = new BigDecimal("1000000.00");
	private static final BigDecimal UNIT_PRICE = new BigDecimal("1000.00");

	@Autowired private OrderService orderService;
	@Autowired private CustomerRepository customerRepository;
	@Autowired private ProductRepository productRepository;
	@Autowired private OrderItemRepository orderItemRepository;

	private String customerId;
	private final List<Long> productIds = new ArrayList<>();

	@BeforeEach
	void 픽스처_준비() {
		customerId = "cc-" + System.nanoTime();
		customerRepository.save(Customer.register(customerId, "{noop}pw", INITIAL_POINT));
		productIds.clear();
		for (int i = 0; i < THREADS; i++) {
			productIds.add(productRepository.save(
					Product.of("cc-product-" + System.nanoTime() + "-" + i, UNIT_PRICE)).getId());
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
	@DisplayName("동일 계정 동시 주문 100건 — 포인트가 정확히 차감된다")
	void 동시_주문_시_포인트가_정확히_차감된다() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(POOL);
		CountDownLatch ready = new CountDownLatch(THREADS);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(THREADS);
		AtomicInteger success = new AtomicInteger();
		// 실패는 뭉뚱그리지 않고 예외 종류별로 센다 — 낙관적 락 충돌과 잔액 부족은 성격이 다르다
		ConcurrentHashMap<String, AtomicInteger> failures = new ConcurrentHashMap<>();

		long began = System.nanoTime();
		for (Long productId : productIds) {
			executor.submit(() -> {
				ready.countDown();
				try {
					// 스레드를 한 지점에 모아 동시에 푼다. 풀 크기(32)에 순차 실행이 섞이면
					// 경합이 약해져 재현되지 않을 수 있다
					start.await();
					orderService.placeOrder(customerId, orderRequest(productId, 1));
					success.incrementAndGet();
				} catch (Exception e) {
					failures.computeIfAbsent(e.getClass().getSimpleName(), k -> new AtomicInteger())
							.incrementAndGet();
				} finally {
					done.countDown();
				}
			});
		}
		// 전원이 배리어에 도달했는지 확인한다. 타임아웃으로 풀리면 동시성이 성립하지 않은 것이므로
		// 그 실행의 수치는 버린다 — 측정이 됐는지부터 단언한다
		assertThat(ready.await(30, TimeUnit.SECONDS))
				.as("스레드 %d개가 모두 출발선에 도달했는가 (실패 시 아래 수치는 무의미하다)", THREADS)
				.isTrue();
		start.countDown();
		done.await(120, TimeUnit.SECONDS);
		long elapsedMs = (System.nanoTime() - began) / 1_000_000;
		executor.shutdown();

		BigDecimal finalPoint = customerRepository.findByCustomerId(customerId).orElseThrow()
				.getCustomerPoint();
		BigDecimal expected = INITIAL_POINT.subtract(UNIT_PRICE.multiply(BigDecimal.valueOf(success.get())));

		System.out.printf("%n[동시성] 스레드 %d · 성공 %d · 실패 %s · %dms%n",
				THREADS, success.get(), failures, elapsedMs);
		System.out.printf("[동시성] 최종 포인트 %s · 기대 %s · 차이 %s%n%n",
				finalPoint.toPlainString(), expected.toPlainString(),
				finalPoint.subtract(expected).toPlainString());

		// ★ 정합성의 정의 — "성공한 건수만큼만" 차감됐는가.
		// 전부 성공했는지가 아니다. 락 충돌로 일부가 실패하는 것은 정상 동작이며,
		// 실패한 주문의 포인트가 차감되거나 성공한 주문이 차감되지 않는 것이 결함이다.
		// BigDecimal 비교는 compareTo — equals는 scale까지 봐서 1000.0 != 1000.00
		assertThat(finalPoint)
				.as("성공 %d건 × %s = %s 만 차감돼야 한다", success.get(), UNIT_PRICE, expected)
				.usingComparator(BigDecimal::compareTo)
				.isEqualTo(expected);
	}

	@Test
	@DisplayName("★ 같은 고객·같은 상품의 첫 주문이 동시에 들어와도 500이 나지 않는다")
	void 같은_상품_첫_주문_경합은_500이_아니다() throws Exception {
		// Phase 6 부하 측정이 찾은 결함이다. 양쪽 모두 findByCustomerAndProduct에서 빈 결과를 받고
		// INSERT를 시도해 복합 UNIQUE에 걸렸고, DataIntegrityViolationException이 그대로 나가
		// **500 + ERROR 로그**가 됐다. check-then-act 경합이다.
		//
		// 이 테스트가 Phase 3에 없었던 이유 — 그때는 스레드마다 **서로 다른 상품**을 주문해
		// 고객 행 경합만 남겼다. 그 설계가 정확히 이 경우를 배제했다.
		Long sharedProductId = productIds.get(0);
		int threads = 30;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);
		ConcurrentHashMap<String, AtomicInteger> outcomes = new ConcurrentHashMap<>();

		for (int i = 0; i < threads; i++) {
			executor.submit(() -> {
				ready.countDown();
				try {
					start.await();
					orderService.placeOrder(customerId, orderRequest(sharedProductId, 1));
					outcomes.computeIfAbsent("성공", k -> new AtomicInteger()).incrementAndGet();
				} catch (Exception e) {
					outcomes.computeIfAbsent(e.getClass().getSimpleName(), k -> new AtomicInteger())
							.incrementAndGet();
				} finally {
					done.countDown();
				}
			});
		}
		assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		assertThat(done.await(120, TimeUnit.SECONDS)).isTrue();
		executor.shutdown();

		System.out.printf("%n[첫 주문 경합] %s%n%n", outcomes);

		// 실패는 낙관적 락 충돌이거나 제약 위반이다. 둘 다 **409로 매핑되는 것**이어야 하고,
		// 그 외의 예외(=500이 될 것)가 나오면 안 된다
		assertThat(outcomes.keySet())
				.as("500이 될 예외가 섞이면 안 된다. 실제 결과: %s", outcomes)
				.allMatch(name -> name.equals("성공")
						|| name.contains("OptimisticLocking")
						|| name.contains("DataIntegrityViolation"));
		assertThat(outcomes.getOrDefault("성공", new AtomicInteger()).get())
				.as("최소 한 건은 성공해야 한다 — 전부 실패하면 경합이 아니라 고장이다")
				.isGreaterThan(0);
	}

	private static OrderRequest orderRequest(Long productId, int quantity) {
		OrderRequest request = new OrderRequest();
		request.setProductId(productId);
		request.setQuantity(quantity);
		return request;
	}
}
