package com.sk.skala.shopapi.customer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.skala.shopapi.customer.dto.CustomerResponse;
import com.sk.skala.shopapi.customer.service.CustomerService;
import com.sk.skala.shopapi.global.auth.SessionHandler;
import com.sk.skala.shopapi.global.exception.Error;
import com.sk.skala.shopapi.global.exception.ResponseException;
import com.sk.skala.shopapi.order.dto.OrderItemDto;
import com.sk.skala.shopapi.order.dto.OrderListDto;
import com.sk.skala.shopapi.order.service.OrderService;

/**
 * 웹 계층만 띄운다. Service는 목이므로 <b>여기서 검증하는 것은 비즈니스 로직이 아니라</b>
 * 바인딩·검증·인증 해석·응답 매핑이다.
 * <p>
 * {@code @Import}가 없다 — {@code @WebMvcTest} 슬라이스는 {@code HandlerMethodArgumentResolver},
 * {@code WebMvcConfigurer}, {@code @ControllerAdvice}를 <b>자동으로 포함한다.</b>
 * 즉 {@code @LoginCustomer} 해석과 예외 매핑이 실제로 살아 있는 상태로 검증된다.
 * (처음엔 셋을 명시적으로 import하고 "없으면 인증이 null로 통과한다"고 적었는데, 빼고 돌려보니
 * 그대로 통과했다. 주석이 틀렸던 것이다 — JOURNAL 2026-08-07)
 * <p>
 * <b>진짜 함정은 다른 곳에 있다.</b> {@code SessionHandler}가 목이므로 {@code getCustomerId()}를
 * 스텁하지 않으면 <b>null이 반환되고 테스트는 통과한다.</b> 인증이 동작한 것이 아니라
 * 아무도 확인하지 않은 것이다. 그래서 "인증되면 로그인 아이디가 Service로 넘어간다"에서
 * <b>넘어간 값 자체를 단언</b>한다 — 401 테스트만으로는 "항상 401"인 코드도 통과한다.
 */
@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;

	@MockBean private CustomerService customerService;
	@MockBean private OrderService orderService;
	@MockBean private SessionHandler sessionHandler;

	private static CustomerResponse response() {
		return CustomerResponse.builder()
				.customerId("skala01").customerPoint(new BigDecimal("1000000.00")).build();
	}

	// ── 인증 ─────────────────────────────────────────────────────────────

	@Test
	@DisplayName("쿠키가 없으면 401 — Service는 호출조차 되지 않는다")
	void 미인증_조회는_401() throws Exception {
		willThrow(new ResponseException(Error.NOT_AUTHENTICATED)).given(sessionHandler).getCustomerId();

		mockMvc.perform(get("/api/customers/skala01"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("NOT_AUTHENTICATED"));

		// 인증이 Controller 앞단에서 끝나는지 — Service가 불렸다면 인증을 Service가 아는 구조다
		then(orderService).should(never()).getCustomerOrders(any(), any());
	}

	@Test
	@DisplayName("@LoginCustomer가 쿠키에서 꺼낸 값이 Service로 전달된다")
	void 인증되면_로그인_아이디가_Service로_넘어간다() throws Exception {
		// ★ 이 테스트가 없으면 위의 401 테스트만으로는 '항상 401'인 코드도 통과한다
		given(sessionHandler.getCustomerId()).willReturn("skala01");
		given(orderService.getCustomerOrders("skala01", "skala01")).willReturn(
				OrderListDto.builder().customerId("skala01").customerPoint(new BigDecimal("970000.00"))
						.products(List.of(OrderItemDto.builder().productId(1L).productName("무선마우스")
								.productPrice(new BigDecimal("15000.00")).quantity(2).build()))
						.build());

		mockMvc.perform(get("/api/customers/skala01"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result").value("success"))
				.andExpect(jsonPath("$.body.products[0].productName").value("무선마우스"))
				.andExpect(jsonPath("$.body.products[0].quantity").value(2));

		then(orderService).should().getCustomerOrders("skala01", "skala01");
	}

	@Test
	@DisplayName("남의 리소스는 403")
	void 소유자가_아니면_403() throws Exception {
		given(sessionHandler.getCustomerId()).willReturn("skala01");
		given(orderService.getCustomerOrders("skala01", "skala02"))
				.willThrow(new ResponseException(Error.NOT_OWNER));

		mockMvc.perform(get("/api/customers/skala02"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("NOT_OWNER"));
	}

	// ── 검증 ─────────────────────────────────────────────────────────────

	@Test
	@DisplayName("필수값이 비면 400이고 어떤 필드인지 알려준다")
	void 빈_가입_요청은_400() throws Exception {
		mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
						.content("{\"customerId\":\"\",\"customerPassword\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("invalid parameter: customerId, customerPassword"));

		then(customerService).should(never()).createCustomer(any());
	}

	@Test
	@DisplayName("깨진 JSON은 500이 아니라 400")
	void 깨진_바디는_400() throws Exception {
		// 이 핸들러가 없으면 일반 핸들러로 떨어져 클라이언트 잘못이 500으로 나간다
		mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
						.content("{not json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("malformed request body"));
	}

	@Test
	@DisplayName("음수 수량 주문은 400")
	void 음수_수량_주문은_400() throws Exception {
		given(sessionHandler.getCustomerId()).willReturn("skala01");

		mockMvc.perform(post("/api/customers/order").contentType(MediaType.APPLICATION_JSON)
						.content("{\"productId\":1,\"quantity\":-5}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("invalid parameter: quantity"));

		then(orderService).should(never()).placeOrder(any(), any());
	}

	// ── 응답 ─────────────────────────────────────────────────────────────

	@Test
	@DisplayName("로그인 응답에 비밀번호가 없고 토큰 발급은 Controller가 한다")
	void 로그인은_토큰을_발급하고_비밀번호를_내보내지_않는다() throws Exception {
		given(customerService.loginCustomer(any())).willReturn(response());

		mockMvc.perform(post("/api/customers/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"customerId\":\"skala01\",\"customerPassword\":\"pw1234\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.customerId").value("skala01"))
				.andExpect(jsonPath("$.body.customerPassword").doesNotExist());

		// 토큰 발급·쿠키 적재는 웹 관심사다 — Service가 아니라 Controller의 몫
		then(sessionHandler).should().storeAccessToken("skala01");
	}

	@Test
	@DisplayName("포인트 부족은 400, 없는 데이터는 404, 낙관적 락 충돌은 409")
	void 에러가_HTTP_상태로_매핑된다() throws Exception {
		given(sessionHandler.getCustomerId()).willReturn("skala01");

		willThrow(new ResponseException(Error.INSUFFICIENT_FUNDS))
				.given(orderService).placeOrder(eq("skala01"), any());
		mockMvc.perform(post("/api/customers/order").contentType(MediaType.APPLICATION_JSON)
						.content("{\"productId\":1,\"quantity\":1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("INSUFFICIENT_FUNDS"));

		given(customerService.updateCustomer(eq("skala01"), any()))
				.willThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(
						Object.class, "skala01"));
		mockMvc.perform(put("/api/customers").contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								java.util.Map.of("customerId", "skala01", "customerPoint", 100))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("CONCURRENT_MODIFICATION"));
	}
}
