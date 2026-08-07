package com.sk.skala.shopapi.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sk.skala.shopapi.common.PagedList;
import com.sk.skala.shopapi.common.Response;
import com.sk.skala.shopapi.common.auth.LoginCustomer;
import com.sk.skala.shopapi.common.auth.SessionHandler;
import com.sk.skala.shopapi.data.dto.CustomerRequest;
import com.sk.skala.shopapi.data.dto.CustomerResponse;
import com.sk.skala.shopapi.data.dto.CustomerSession;
import com.sk.skala.shopapi.data.dto.CustomerUpdateRequest;
import com.sk.skala.shopapi.data.dto.OrderListDto;
import com.sk.skala.shopapi.data.dto.OrderRequest;
import com.sk.skala.shopapi.service.CustomerService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

	private final CustomerService customerService;
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
		return Response.success(customerService.getCustomerById(customerId));
	}

	@PostMapping
	public Response<CustomerResponse> createCustomer(@RequestBody CustomerRequest request) {
		return Response.success(customerService.createCustomer(request));
	}

	@PostMapping("/login")
	public Response<CustomerResponse> loginCustomer(@RequestBody CustomerSession customerSession) {
		CustomerResponse customer = customerService.loginCustomer(customerSession);
		sessionHandler.storeAccessToken(customer.getCustomerId());
		return Response.success(customer);
	}

	@PutMapping
	public Response<CustomerResponse> updateCustomer(@RequestBody CustomerUpdateRequest request) {
		return Response.success(customerService.updateCustomer(request));
	}

	@DeleteMapping
	public Response<Void> deleteCustomer(@RequestBody CustomerRequest request) {
		customerService.deleteCustomer(request);
		return Response.success();
	}

	// 쿠키·JWT 해석은 ArgumentResolver가 끝낸다. Service는 customerId만 받는다
	@PostMapping("/order")
	public Response<Void> placeOrder(@LoginCustomer String customerId,
			@RequestBody OrderRequest order) {
		customerService.placeOrder(customerId, order);
		return Response.success();
	}

	@PostMapping("/cancel")
	public Response<Void> cancelOrder(@LoginCustomer String customerId,
			@RequestBody OrderRequest order) {
		customerService.cancelOrder(customerId, order);
		return Response.success();
	}
}
