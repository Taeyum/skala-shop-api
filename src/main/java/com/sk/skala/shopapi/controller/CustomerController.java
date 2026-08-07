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

	@GetMapping("/list")
	public Response<PagedList<CustomerResponse>> getAllCustomers(
			@RequestParam(value = "offset", defaultValue = "0") int offset,
			@RequestParam(value = "count", defaultValue = "10") int count) {
		return customerService.getAllCustomers(offset, count);
	}

	@GetMapping("/{customerId}")
	public Response<OrderListDto> getCustomerById(@PathVariable String customerId) {
		return customerService.getCustomerById(customerId);
	}

	@PostMapping
	public Response<CustomerResponse> createCustomer(@RequestBody CustomerRequest request) {
		return customerService.createCustomer(request);
	}

	// 토큰 발급·쿠키 적재는 SessionHandler가 응답 객체를 직접 받아 처리한다
	@PostMapping("/login")
	public Response<CustomerResponse> loginCustomer(@RequestBody CustomerSession customerSession) {
		return customerService.loginCustomer(customerSession);
	}

	@PutMapping
	public Response<CustomerResponse> updateCustomer(@RequestBody CustomerUpdateRequest request) {
		return customerService.updateCustomer(request);
	}

	@DeleteMapping
	public Response<Void> deleteCustomer(@RequestBody CustomerRequest request) {
		return customerService.deleteCustomer(request);
	}

	@PostMapping("/order")
	public Response<Void> placeOrder(@RequestBody OrderRequest order) {
		return customerService.placeOrder(order);
	}

	@PostMapping("/cancel")
	public Response<Void> cancelOrder(@RequestBody OrderRequest order) {
		return customerService.cancelOrder(order);
	}
}
