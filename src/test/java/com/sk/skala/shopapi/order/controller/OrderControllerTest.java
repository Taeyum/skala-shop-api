package com.sk.skala.shopapi.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.sk.skala.shopapi.global.auth.SessionHandler;
import com.sk.skala.shopapi.global.exception.Error;
import com.sk.skala.shopapi.global.exception.ResponseException;
import com.sk.skala.shopapi.order.dto.OrderItemDto;
import com.sk.skala.shopapi.order.dto.OrderListDto;
import com.sk.skala.shopapi.order.service.OrderService;

/**
 * URI는 {@code /api/customers/*}지만 소유 도메인은 order다 (패키지 순환 해소).
 * <p>
 * {@code @Import}를 쓰지 않는다 — 슬라이스가 {@code HandlerMethodArgumentResolver},
 * {@code WebMvcConfigurer}, {@code @ControllerAdvice}를 자동 포함하므로
 * {@code @LoginCustomer} 해석과 예외 매핑이 실제로 살아 있는 상태다.
 * <p>
 * <b>함정</b> — {@code SessionHandler}가 목이라 {@code getCustomerId()}를 스텁하지 않으면
 * null이 반환되고 테스트는 통과한다. 인증이 동작한 것이 아니라 아무도 확인하지 않은 것이다.
 * 그래서 <b>Service로 넘어간 값 자체를 단언</b>한다.
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

	@Autowired private MockMvc mockMvc;

	@MockBean private OrderService orderService;
	@MockBean private SessionHandler sessionHandler;

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

	@Test
	@DisplayName("음수 수량 주문은 400이고 Service까지 가지 않는다")
	void 음수_수량_주문은_400() throws Exception {
		given(sessionHandler.getCustomerId()).willReturn("skala01");

		mockMvc.perform(post("/api/customers/order").contentType(MediaType.APPLICATION_JSON)
						.content("{\"productId\":1,\"quantity\":-5}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("invalid parameter: quantity"));

		then(orderService).should(never()).placeOrder(any(), any());
	}

	@Test
	@DisplayName("포인트 부족은 400, 취소 수량 초과는 400, 낙관적 락 충돌은 409")
	void 에러가_HTTP_상태로_매핑된다() throws Exception {
		given(sessionHandler.getCustomerId()).willReturn("skala01");

		willThrow(new ResponseException(Error.INSUFFICIENT_FUNDS))
				.given(orderService).placeOrder(eq("skala01"), any());
		mockMvc.perform(post("/api/customers/order").contentType(MediaType.APPLICATION_JSON)
						.content("{\"productId\":1,\"quantity\":1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("INSUFFICIENT_FUNDS"));

		willThrow(new ResponseException(Error.INSUFFICIENT_QUANTITY))
				.given(orderService).cancelOrder(eq("skala01"), any());
		mockMvc.perform(post("/api/customers/cancel").contentType(MediaType.APPLICATION_JSON)
						.content("{\"productId\":1,\"quantity\":99}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("INSUFFICIENT_QUANTITY"));

		willThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(
				Object.class, "skala01")).given(orderService).placeOrder(eq("skala01"), any());
		mockMvc.perform(post("/api/customers/order").contentType(MediaType.APPLICATION_JSON)
						.content("{\"productId\":1,\"quantity\":1}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("CONCURRENT_MODIFICATION"));
	}
}
