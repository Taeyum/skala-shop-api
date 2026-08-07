package com.sk.skala.shopapi.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sk.skala.shopapi.customer.entity.Customer;
import com.sk.skala.shopapi.customer.service.CustomerService;
import com.sk.skala.shopapi.global.exception.Error;
import com.sk.skala.shopapi.global.exception.ResponseException;
import com.sk.skala.shopapi.order.dto.OrderListDto;
import com.sk.skala.shopapi.order.dto.OrderRequest;
import com.sk.skala.shopapi.order.entity.OrderItem;
import com.sk.skala.shopapi.order.repository.OrderItemRepository;
import com.sk.skala.shopapi.product.entity.Product;
import com.sk.skala.shopapi.product.service.ProductService;

/**
 * 주문 도메인 단위 테스트.
 * <p>
 * 협력자를 {@code CustomerService}·{@code ProductService}로 모킹한다 — <b>Repository가 아니다.</b>
 * 다른 도메인의 Repository를 물지 않는 구조라서 이렇게 모킹할 수 있고,
 * MSA로 분리하면 이 자리가 그대로 Client 목이 된다.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

	@Mock private OrderItemRepository orderItemRepository;
	@Mock private CustomerService customerService;
	@Mock private ProductService productService;
	@InjectMocks private OrderService orderService;

	private Customer customer;
	private Product product;

	@BeforeEach
	void setUp() {
		customer = Customer.register("skala01", "encoded", new BigDecimal("1000000.00"));
		product = Product.of("무선마우스", new BigDecimal("15000.00"));
	}

	private static OrderRequest request(Long productId, int quantity) {
		OrderRequest request = new OrderRequest();
		request.setProductId(productId);
		request.setQuantity(quantity);
		return request;
	}

	@Test
	void 첫_주문은_새_OrderItem을_만든다() {
		given(customerService.findCustomer("skala01")).willReturn(customer);
		given(productService.findProduct(1L)).willReturn(product);
		given(orderItemRepository.findByCustomerAndProduct(customer, product)).willReturn(Optional.empty());

		orderService.placeOrder("skala01", request(1L, 2));

		assertThat(customer.getCustomerPoint()).usingComparator(BigDecimal::compareTo)
				.isEqualTo(new BigDecimal("970000.00"));
		then(orderItemRepository).should().save(org.mockito.ArgumentMatchers.argThat(
				item -> item.getQuantity() == 2));
	}

	@Test
	void 같은_상품_재주문은_신규_행_대신_수량을_누적한다() {
		OrderItem existing = OrderItem.of(customer, product, 2, new BigDecimal("30000.00"));
		given(customerService.findCustomer("skala01")).willReturn(customer);
		given(productService.findProduct(1L)).willReturn(product);
		given(orderItemRepository.findByCustomerAndProduct(customer, product))
				.willReturn(Optional.of(existing));

		orderService.placeOrder("skala01", request(1L, 3));

		assertThat(existing.getQuantity()).isEqualTo(5);
		assertThat(existing.getOrderedAmount()).usingComparator(BigDecimal::compareTo)
				.isEqualTo(new BigDecimal("75000.00"));
	}

	@Test
	void 잔액이_부족하면_주문이_저장되지_않는다() {
		Customer poor = Customer.register("poor", "encoded", new BigDecimal("1000.00"));
		given(customerService.findCustomer("poor")).willReturn(poor);
		given(productService.findProduct(1L)).willReturn(product);

		assertThatThrownBy(() -> orderService.placeOrder("poor", request(1L, 1)))
				.isInstanceOf(ResponseException.class)
				.extracting(e -> ((ResponseException) e).getError())
				.isEqualTo(Error.INSUFFICIENT_FUNDS);

		// 잔액 검사가 저장보다 먼저 일어나는지 — 순서가 바뀌면 결제 없는 주문이 남는다
		then(orderItemRepository).should(never()).save(any());
	}

	@Test
	void 부분_취소는_수량을_줄이고_행을_남긴다() {
		OrderItem existing = OrderItem.of(customer, product, 2, new BigDecimal("30000.00"));
		given(customerService.findCustomer("skala01")).willReturn(customer);
		given(productService.findProduct(1L)).willReturn(product);
		given(orderItemRepository.findByCustomerAndProduct(customer, product))
				.willReturn(Optional.of(existing));

		orderService.cancelOrder("skala01", request(1L, 1));

		assertThat(existing.getQuantity()).isEqualTo(1);
		assertThat(customer.getCustomerPoint()).usingComparator(BigDecimal::compareTo)
				.isEqualTo(new BigDecimal("1015000.00"));
		then(orderItemRepository).should().save(existing);
		then(orderItemRepository).should(never()).delete(any());
	}

	@Test
	void 전량_취소는_행을_삭제한다() {
		OrderItem existing = OrderItem.of(customer, product, 2, new BigDecimal("30000.00"));
		given(customerService.findCustomer("skala01")).willReturn(customer);
		given(productService.findProduct(1L)).willReturn(product);
		given(orderItemRepository.findByCustomerAndProduct(customer, product))
				.willReturn(Optional.of(existing));

		orderService.cancelOrder("skala01", request(1L, 2));

		then(orderItemRepository).should().delete(existing);
		then(orderItemRepository).should(never()).save(any());
	}

	@Test
	void 주문한_적_없는_상품_취소는_DATA_NOT_FOUND() {
		given(customerService.findCustomer("skala01")).willReturn(customer);
		given(productService.findProduct(1L)).willReturn(product);
		given(orderItemRepository.findByCustomerAndProduct(customer, product)).willReturn(Optional.empty());

		assertThatThrownBy(() -> orderService.cancelOrder("skala01", request(1L, 1)))
				.isInstanceOf(ResponseException.class)
				.extracting(e -> ((ResponseException) e).getError())
				.isEqualTo(Error.DATA_NOT_FOUND);
	}

	@Test
	void 주문_조회는_소유권을_먼저_확인한다() {
		// requireOwner가 던지면 findCustomer까지 가지 않는다.
		// 이 순서가 뒤집히면 남의 계정 존재 여부가 404/403으로 노출된다
		org.mockito.BDDMockito.willThrow(new ResponseException(Error.NOT_OWNER))
				.given(customerService).requireOwner("skala01", "skala02");

		assertThatThrownBy(() -> orderService.getCustomerOrders("skala01", "skala02"))
				.isInstanceOf(ResponseException.class);

		then(customerService).should(never()).findCustomer(any());
	}

	@Test
	void 보유_상품_목록을_DTO로_조립한다() {
		OrderItem item = OrderItem.of(customer, product, 2, new BigDecimal("30000.00"));
		given(customerService.findCustomer("skala01")).willReturn(customer);
		given(orderItemRepository.findByCustomer(customer)).willReturn(List.of(item));

		OrderListDto result = orderService.getCustomerOrders("skala01", "skala01");

		assertThat(result.getCustomerId()).isEqualTo("skala01");
		assertThat(result.getProducts()).hasSize(1);
		assertThat(result.getProducts().get(0).getProductName()).isEqualTo("무선마우스");
		assertThat(result.getProducts().get(0).getQuantity()).isEqualTo(2);
	}
}
