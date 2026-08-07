package com.sk.skala.shopapi.customer.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
import com.sk.skala.shopapi.customer.service.CustomerService;
import com.sk.skala.shopapi.global.config.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 고객 도메인의 웹 진입점.
 * <p>
 * 주문 관련 엔드포인트({@code /{customerId}} 조회, {@code /order}, {@code /cancel})는
 * URI가 {@code /api/customers/*}이지만 <b>주문 도메인이 소유한다</b> —
 * {@link com.sk.skala.shopapi.order.controller.OrderController}.
 * 여기 두었더니 customer → order → customer 패키지 순환이 생겼고 ArchUnit이 잡아냈다.
 */
@Tag(name = "2. 고객", description = "가입·로그인·정보 수정·탈퇴")
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

	private final CustomerService customerService;
	// 토큰 발급·쿠키 적재는 웹 관심사다. Service가 아니라 여기가 제자리다
	private final SessionHandler sessionHandler;

	@Operation(summary = "전체 고객 목록 — **인증 없이 열려 있다** (알려진 한계, SPEC 1절)")
	@GetMapping("/list")
	public Response<PagedList<CustomerResponse>> getAllCustomers(
			@RequestParam(value = "offset", defaultValue = "0") int offset,
			@RequestParam(value = "count", defaultValue = "10") int count) {
		return Response.success(customerService.getAllCustomers(offset, count));
	}

	@Operation(summary = "회원가입 — 초기 포인트는 서버가 정한다")
	@PostMapping
	public Response<CustomerResponse> createCustomer(@Valid @RequestBody CustomerRequest request) {
		return Response.success(customerService.createCustomer(request));
	}

	@Operation(summary = "로그인 — 성공하면 bff-access 쿠키가 발급된다. **여기서 먼저 실행하라**")
	@PostMapping("/login")
	public Response<CustomerResponse> loginCustomer(@Valid @RequestBody CustomerSession customerSession) {
		CustomerResponse customer = customerService.loginCustomer(customerSession);
		sessionHandler.storeAccessToken(customer.getCustomerId());
		return Response.success(customer);
	}

	// SPEC 1절이 "본인만"으로 표시한 항목 — @LoginCustomer로 인증을 강제하고 소유권을 확인한다
	@Operation(summary = "정보 수정 — 본인만")
	@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
	@PutMapping
	public Response<CustomerResponse> updateCustomer(@LoginCustomer String loginCustomerId,
			@Valid @RequestBody CustomerUpdateRequest request) {
		return Response.success(customerService.updateCustomer(loginCustomerId, request));
	}

	// 탈퇴는 customerId만 보내므로 @Valid를 걸지 않는다 — 걸면 customerPassword까지 요구하게 된다
	@Operation(summary = "탈퇴 — 보유 상품이 있으면 409 DATA_IN_USE")
	@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
	@DeleteMapping
	public Response<Void> deleteCustomer(@LoginCustomer String loginCustomerId,
			@RequestBody CustomerRequest request) {
		customerService.deleteCustomer(loginCustomerId, request);
		return Response.success();
	}
}
