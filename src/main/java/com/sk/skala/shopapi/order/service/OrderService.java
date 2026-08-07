package com.sk.skala.shopapi.order.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sk.skala.shopapi.customer.entity.Customer;
import com.sk.skala.shopapi.customer.service.CustomerService;
import com.sk.skala.shopapi.global.exception.Error;
import com.sk.skala.shopapi.global.exception.ParameterException;
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
	public OrderListDto getCustomerOrders(String customerId) {
		Customer customer = customerService.findCustomer(customerId);

		List<OrderItemDto> products = new ArrayList<>();
		for (OrderItem item : orderItemRepository.findByCustomer(customer)) {
			// item.getProduct()마다 SELECT가 나간다 (N+1) — Phase 3에서 fetch join으로 개선
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
		validate(order);

		Customer customer = customerService.findCustomer(customerId);
		Product product = productService.findProduct(order.getProductId());

		// 수량이 음수여도 막지 않는다 — 포인트가 늘어난다. Phase 2에서 @Positive로 차단
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
		validate(order);

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

	private void validate(OrderRequest order) {
		if (order.getProductId() == null || order.getQuantity() == null) {
			throw new ParameterException("productId, quantity");
		}
	}
}
