package com.sk.skala.shopapi.order.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sk.skala.shopapi.customer.entity.Customer;
import com.sk.skala.shopapi.customer.service.CustomerService;
import com.sk.skala.shopapi.global.exception.Error;
import com.sk.skala.shopapi.global.exception.ResponseException;
import com.sk.skala.shopapi.order.dto.OrderItemDto;
import com.sk.skala.shopapi.order.dto.OrderListDto;
import com.sk.skala.shopapi.order.dto.OrderRequest;
import com.sk.skala.shopapi.order.entity.OrderItem;
import com.sk.skala.shopapi.order.repository.OrderItemRepository;
import com.sk.skala.shopapi.product.entity.Product;
import com.sk.skala.shopapi.product.service.ProductService;

import lombok.RequiredArgsConstructor;

/**
 * 주문 도메인. 고객·상품 양쪽에 걸쳐 있어 두 도메인의 <b>Service를 경유</b>한다.
 * <p>
 * 의존 방향은 <b>order → customer, order → product 단방향</b>이다. 역방향이 생기면 순환이 되므로
 * customer·product는 order를 알지 않는다 (DECISIONS.md 4절).
 */
@Service
@RequiredArgsConstructor
public class OrderService {

	private final OrderItemRepository orderItemRepository;
	// 다른 도메인의 Repository가 아니라 Service를 문다 — MSA 분리 시 이 자리가 Client 구현으로 바뀐다
	private final CustomerService customerService;
	private final ProductService productService;

	/**
	 * 고객 정보 + 보유 상품 목록. 고객 데이터와 주문 데이터를 모두 읽으므로 주문 도메인이 조립한다.
	 * 고객 도메인이 조립하면 customer → order 의존이 생겨 순환이 된다.
	 */
	@Transactional(readOnly = true)
	public OrderListDto getCustomerOrders(String loginCustomerId, String customerId) {
		// 주문 이력과 잔액은 개인정보다. PUT·DELETE와 같은 BOLA 방어를 적용한다 (DECISIONS.md 9-5절)
		customerService.requireOwner(loginCustomerId, customerId);
		Customer customer = customerService.findCustomer(customerId);

		List<OrderItemDto> products = new ArrayList<>();
		// findByCustomer에 @EntityGraph(product)가 걸려 있어 아래 getProduct()는 추가 쿼리를 내지 않는다.
		// 없던 시절엔 상품 종수만큼 SELECT가 더 나갔다 (20종 → 22개, docs/evidence/n-plus-1.md)
		for (OrderItem item : orderItemRepository.findByCustomer(customer)) {
			Product product = item.getProduct();
			products.add(OrderItemDto.builder()
					.productId(product.getId())
					.productName(product.getProductName())
					.productPrice(product.getProductPrice())
					.quantity(item.getQuantity())
					.build());
		}

		return OrderListDto.builder()
				.customerId(customer.getCustomerId())
				.customerPoint(customer.getCustomerPoint())
				.products(products)
				.build();
	}

	@Transactional
	public void placeOrder(String customerId, OrderRequest order) {
		Customer customer = customerService.findCustomer(customerId);
		Product product = productService.findProduct(order.getProductId());

		// 수량 검증은 @Positive(웹 진입)와 엔티티 불변식 두 계층이 막는다 (DECISIONS.md 9-4절)
		BigDecimal total = product.getProductPrice()
				.multiply(BigDecimal.valueOf(order.getQuantity()));
		// 잔액 부족 판단은 Customer가 한다 — 여기서 검증을 잊어도 포인트는 음수가 되지 않는다
		customer.usePoint(total);

		OrderItem item = orderItemRepository.findByCustomerAndProduct(customer, product)
				.orElse(null);
		if (item == null) {
			item = OrderItem.of(customer, product, order.getQuantity(), total);
		} else {
			// 재주문은 신규 행이 아니라 수량 누적 — (customer_id, product_id) 복합 UNIQUE가 강제한다
			item.addOrder(order.getQuantity(), total);
		}
		orderItemRepository.save(item);
	}

	@Transactional
	public void cancelOrder(String customerId, OrderRequest order) {
		Customer customer = customerService.findCustomer(customerId);
		Product product = productService.findProduct(order.getProductId());

		OrderItem item = orderItemRepository.findByCustomerAndProduct(customer, product)
				.orElseThrow(() -> new ResponseException(Error.DATA_NOT_FOUND, "OrderItem not found"));

		// 보유 수량 검증·환불액 계산·반올림 규칙이 모두 OrderItem 안에 있다.
		// 현재가가 아니라 결제 총액에서 환불하는 이유는 DECISIONS.md 2절
		BigDecimal refund = item.cancel(order.getQuantity());
		customer.refundPoint(refund);

		if (item.isEmpty()) {
			orderItemRepository.delete(item);
		} else {
			orderItemRepository.save(item);
		}
	}
}
