package com.sk.skala.shopapi.support;

import java.math.BigDecimal;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Transactional;

import com.sk.skala.shopapi.customer.entity.Customer;
import com.sk.skala.shopapi.order.entity.OrderItem;
import com.sk.skala.shopapi.order.repository.OrderItemRepository;
import com.sk.skala.shopapi.product.entity.Product;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

/**
 * 비관적 락 비교 실험용. <b>운영 코드가 아니라 테스트 소스에 둔다.</b>
 * <p>
 * 비교를 위해 {@code OrderService.placeOrder}와 <b>같은 일을 같은 순서로</b> 하되
 * 고객을 읽는 방식만 바꾼다 — {@code SELECT ... FOR UPDATE}로 행을 선점한다.
 * 실험 때문에 운영 경로에 쓰지 않는 분기를 남기지 않기 위해 여기에 복제했다.
 * 복제한 로직이 원본과 어긋나면 비교가 무의미해지므로, 원본이 바뀌면 여기도 함께 본다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PessimisticOrderService {

	@Bean
	PessimisticOrderer pessimisticOrderer(OrderItemRepository orderItemRepository) {
		return new PessimisticOrderer(orderItemRepository);
	}

	public static class PessimisticOrderer {

		@PersistenceContext
		private EntityManager entityManager;

		private final OrderItemRepository orderItemRepository;

		PessimisticOrderer(OrderItemRepository orderItemRepository) {
			this.orderItemRepository = orderItemRepository;
		}

		@Transactional
		public void placeOrder(String customerId, Long productId, int quantity) {
			// 여기가 유일한 차이다 — 행을 잠그고 읽는다. 다른 트랜잭션은 이 행에서 대기한다
			Customer customer = entityManager
					.createQuery("select c from Customer c where c.customerId = :cid", Customer.class)
					.setParameter("cid", customerId)
					.setLockMode(LockModeType.PESSIMISTIC_WRITE)
					.getSingleResult();
			Product product = entityManager.find(Product.class, productId);

			BigDecimal total = product.getProductPrice().multiply(BigDecimal.valueOf(quantity));
			customer.usePoint(total);

			OrderItem item = orderItemRepository.findByCustomerAndProduct(customer, product)
					.orElse(null);
			if (item == null) {
				item = OrderItem.of(customer, product, quantity, total);
			} else {
				item.addOrder(quantity, total);
			}
			orderItemRepository.save(item);
		}
	}
}
