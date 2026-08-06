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

import com.sk.skala.shopapi.data.Customer;
import com.sk.skala.shopapi.data.CustomerOrder;
import com.sk.skala.shopapi.data.OrderRequest;
import com.sk.skala.shopapi.service.CustomerService;
import com.sk.skala.shopapi.tools.PagedList;
import com.sk.skala.shopapi.tools.Response;
import com.sk.skala.shopapi.tools.SessionHandler;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

	private final CustomerService customerService;
	private final SessionHandler sessionHandler;

	@GetMapping("/list")
	public Response<PagedList<Customer>> getCustomers(
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(defaultValue = "10") int count) {
		// 엔티티를 그대로 내보내 비밀번호가 응답에 실린다 — Phase 2에서 차단
		return Response.success(customerService.getCustomers(offset, count));
	}

	@GetMapping("/{customerId}")
	public Response<CustomerOrder> getCustomerOrders(@PathVariable String customerId) {
		return Response.success(customerService.getCustomerOrders(customerId));
	}

	@PostMapping
	public Response<Customer> createCustomer(@RequestBody Customer customer) {
		return Response.success(customerService.createCustomer(customer));
	}

	@PostMapping("/login")
	public Response<Void> login(@RequestBody Customer customer, HttpServletResponse response) {
		sessionHandler.writeCookie(response, customerService.login(customer));
		// 응답 바디에 고객 정보를 싣지 않는다 — 비밀번호 노출 금지 (SPEC.md 2절)
		return Response.success();
	}

	@PutMapping
	public Response<Customer> updateCustomer(@RequestBody Customer customer) {
		return Response.success(customerService.updateCustomer(customer));
	}

	@DeleteMapping
	public Response<Void> deleteCustomer(@RequestBody Customer customer) {
		customerService.deleteCustomer(customer);
		return Response.success();
	}

	@PostMapping("/order")
	public Response<Void> order(@RequestBody OrderRequest request) {
		customerService.order(request);
		return Response.success();
	}

	@PostMapping("/cancel")
	public Response<Void> cancel(@RequestBody OrderRequest request) {
		customerService.cancel(request);
		return Response.success();
	}
}
