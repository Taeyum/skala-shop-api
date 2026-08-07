package com.sk.skala.shopapi.order.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sk.skala.shopapi.global.auth.LoginCustomer;
import com.sk.skala.shopapi.global.common.Response;
import com.sk.skala.shopapi.global.config.OpenApiConfig;
import com.sk.skala.shopapi.order.dto.OrderListDto;
import com.sk.skala.shopapi.order.dto.OrderRequest;
import com.sk.skala.shopapi.order.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 주문 도메인의 웹 진입점.
 * <p>
 * <b>URI는 {@code /api/customers/*} 그대로다</b> — SPEC.md가 고정한 계약이므로 바꾸지 않는다.
 * 바뀐 것은 <b>이 코드가 어느 도메인에 속하는가</b>뿐이다.
 * <p>
 * 원래 이 세 엔드포인트는 {@code CustomerController}에 있었다. 그 결과
 * {@code customer.controller → order.service → customer.service} 라는 <b>패키지 순환</b>이
 * 생겼는데, {@code DECISIONS.md} 4절은 "의존은 order → customer 단방향"이라고 적고 있었다.
 * <b>문서와 코드가 달랐고, 그것을 찾아낸 것은 사람이 아니라 ArchUnit의 순환 검사다</b>
 * (JOURNAL 2026-08-07).
 * <p>
 * MSA로 나눌 때도 이 배치가 맞다 — {@code /api/customers/order}를 서빙하는 것은
 * 고객 서비스가 아니라 주문 서비스다. URI 접두사와 소유 도메인은 별개다.
 */
@Tag(name = "3. 주문", description = "주문·취소·보유 상품 조회. URI는 /api/customers/* 이지만 주문 도메인이 소유한다")
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class OrderController {

	private final OrderService orderService;

	// 자료는 인증 불필요로 두었으나 주문 이력·잔액은 개인정보다.
	// PUT·DELETE에 BOLA 방어를 추가한 것과 같은 이유로 본인만 조회하게 한다 (SPEC.md 1절 주석)
	@Operation(summary = "고객 정보 + 보유 상품 목록 — 본인만")
	@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
	@GetMapping("/{customerId}")
	public Response<OrderListDto> getCustomerById(@LoginCustomer String loginCustomerId,
			@PathVariable String customerId) {
		return Response.success(orderService.getCustomerOrders(loginCustomerId, customerId));
	}

	// 쿠키·JWT 해석은 ArgumentResolver가 끝낸다. Service는 customerId만 받는다
	@Operation(summary = "주문 — 포인트로 결제. 같은 상품 재주문은 수량 누적")
	@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
	@PostMapping("/order")
	public Response<Void> placeOrder(@LoginCustomer String customerId,
			@Valid @RequestBody OrderRequest order) {
		orderService.placeOrder(customerId, order);
		return Response.success();
	}

	@Operation(summary = "취소 — 주문 당시 가격으로 환불. 0이 되면 행 삭제")
	@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
	@PostMapping("/cancel")
	public Response<Void> cancelOrder(@LoginCustomer String customerId,
			@Valid @RequestBody OrderRequest order) {
		orderService.cancelOrder(customerId, order);
		return Response.success();
	}
}
