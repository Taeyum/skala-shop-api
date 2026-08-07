package com.sk.skala.shopapi.customer.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sk.skala.shopapi.global.common.PagedList;
import com.sk.skala.shopapi.global.common.Response;
import com.sk.skala.shopapi.global.auth.LoginCustomer;
import com.sk.skala.shopapi.global.auth.SessionHandler;
import com.sk.skala.shopapi.customer.dto.CustomerRequest;
import com.sk.skala.shopapi.customer.dto.CustomerResponse;
import com.sk.skala.shopapi.customer.dto.CustomerSession;
import com.sk.skala.shopapi.customer.dto.CustomerUpdateRequest;
import com.sk.skala.shopapi.order.dto.OrderListDto;
import com.sk.skala.shopapi.order.dto.OrderRequest;
import com.sk.skala.shopapi.order.service.OrderService;
import com.sk.skala.shopapi.customer.service.CustomerService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

	private final CustomerService customerService;
	// 고객 + 보유 상품 조회와 주문·취소는 주문 도메인이 담당한다
	private final OrderService orderService;
	// 토큰 발급·쿠키 적재는 웹 관심사다. Service가 아니라 여기가 제자리다
	private final SessionHandler sessionHandler;

	@GetMapping("/list")
	public Response<PagedList<CustomerResponse>> getAllCustomers(
			@RequestParam(value = "offset", defaultValue = "0") int offset,
			@RequestParam(value = "count", defaultValue = "10") int count) {
		return Response.success(customerService.getAllCustomers(offset, count));
	}

	@GetMapping("/{customerId}")
	public Response<OrderListDto> getCustomerById(@PathVariable String customerId) {
		return Response.success(orderService.getCustomerOrders(customerId));
	}

	@PostMapping
	public Response<CustomerResponse> createCustomer(@Valid @RequestBody CustomerRequest request) {
		return Response.success(customerService.createCustomer(request));
	}

	@PostMapping("/login")
	public Response<CustomerResponse> loginCustomer(@Valid @RequestBody CustomerSession customerSession) {
		CustomerResponse customer = customerService.loginCustomer(customerSession);
		sessionHandler.storeAccessToken(customer.getCustomerId());
		return Response.success(customer);
	}

	@PutMapping
	public Response<CustomerResponse> updateCustomer(@Valid @RequestBody CustomerUpdateRequest request) {
		return Response.success(customerService.updateCustomer(request));
	}

	// 탈퇴는 customerId만 보내므로 @Valid를 걸지 않는다 — 걸면 customerPassword까지 요구하게 된다
	@DeleteMapping
	public Response<Void> deleteCustomer(@RequestBody CustomerRequest request) {
		customerService.deleteCustomer(request);
		return Response.success();
	}

	// 쿠키·JWT 해석은 ArgumentResolver가 끝낸다. Service는 customerId만 받는다
	@PostMapping("/order")
	public Response<Void> placeOrder(@LoginCustomer String customerId,
			@Valid @RequestBody OrderRequest order) {
		orderService.placeOrder(customerId, order);
		return Response.success();
	}

	@PostMapping("/cancel")
	public Response<Void> cancelOrder(@LoginCustomer String customerId,
			@Valid @RequestBody OrderRequest order) {
		orderService.cancelOrder(customerId, order);
		return Response.success();
	}
}
