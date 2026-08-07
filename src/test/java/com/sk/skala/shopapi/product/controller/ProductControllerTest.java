package com.sk.skala.shopapi.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

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
import com.sk.skala.shopapi.product.dto.ProductResponse;
import com.sk.skala.shopapi.product.service.ProductService;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

	@Autowired private MockMvc mockMvc;
	@MockBean private ProductService productService;
	/**
	 * 이 컨트롤러는 인증을 쓰지 않는데도 목이 필요하다 — {@code WebConfig}가
	 * {@code WebMvcConfigurer}라서 슬라이스에 자동 포함되고, 그것이
	 * {@code LoginCustomerArgumentResolver}를, 다시 {@code SessionHandler}를 끌어온다.
	 * 즉 <b>모든 @WebMvcTest에 인증 해석기가 실제로 살아 있다.</b> 이 컨트롤러의
	 * 엔드포인트에는 {@code @LoginCustomer} 파라미터가 없어 해석기가 호출되지 않을 뿐이다 —
	 * "인증이 없는 API"임이 이 테스트로 확인된다 (스텁하지 않은 목이 예외를 던지지 않는다).
	 */
	@MockBean private SessionHandler sessionHandler;

	@Test
	@DisplayName("상품 조회는 인증이 필요 없다 (SPEC 1절)")
	void 상품_조회는_인증_없이_된다() throws Exception {
		given(productService.getProductById(1L)).willReturn(
				ProductResponse.builder().id(1L).productName("무선마우스")
						.productPrice(new BigDecimal("15000.00")).build());

		mockMvc.perform(get("/api/products/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.productName").value("무선마우스"));
	}

	@Test
	@DisplayName("페이징 기본값은 offset=0, count=10")
	void 페이징_파라미터_기본값() throws Exception {
		mockMvc.perform(get("/api/products/list")).andExpect(status().isOk());

		then(productService).should().getAllProducts(0, 10);
	}

	@Test
	@DisplayName("없는 상품은 404")
	void 없는_상품은_404() throws Exception {
		given(productService.getProductById(99L))
				.willThrow(new ResponseException(Error.DATA_NOT_FOUND));

		mockMvc.perform(get("/api/products/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("DATA_NOT_FOUND"));
	}

	@Test
	@DisplayName("빈 상품명·음수 가격은 400이고 Service까지 가지 않는다")
	void 잘못된_등록_요청은_400() throws Exception {
		mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
						.content("{\"productName\":\"\",\"productPrice\":-100}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("invalid parameter: productName, productPrice"));

		then(productService).should(never()).createProduct(any());
	}

	@Test
	@DisplayName("중복 상품명은 409")
	void 중복_상품명은_409() throws Exception {
		willThrow(new ResponseException(Error.DATA_DUPLICATED)).given(productService).createProduct(any());

		mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
						.content("{\"productName\":\"무선마우스\",\"productPrice\":15000}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("DATA_DUPLICATED"));
	}

	@Test
	@DisplayName("수정은 body의 id로 대상을 찾는다 (URI에 id가 없다 — SPEC 계약)")
	void 수정은_바디의_id를_쓴다() throws Exception {
		given(productService.updateProduct(any())).willReturn(
				ProductResponse.builder().id(1L).productName("유선마우스")
						.productPrice(new BigDecimal("9000.00")).build());

		mockMvc.perform(put("/api/products").contentType(MediaType.APPLICATION_JSON)
						.content("{\"id\":1,\"productName\":\"유선마우스\",\"productPrice\":9000}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.productName").value("유선마우스"));
	}

	@Test
	@DisplayName("주문된 상품 삭제는 409 — 참조 무결성 거부")
	void 주문된_상품_삭제는_409() throws Exception {
		willThrow(new ResponseException(Error.DATA_IN_USE)).given(productService).deleteProduct(any());

		mockMvc.perform(delete("/api/products").contentType(MediaType.APPLICATION_JSON)
						.content("{\"id\":1}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("DATA_IN_USE"));
	}

	@Test
	@DisplayName("삭제 성공은 body 없이 success")
	void 삭제_성공() throws Exception {
		mockMvc.perform(delete("/api/products").contentType(MediaType.APPLICATION_JSON)
						.content("{\"id\":1}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result").value("success"));

		then(productService).should().deleteProduct(any());
	}
}
