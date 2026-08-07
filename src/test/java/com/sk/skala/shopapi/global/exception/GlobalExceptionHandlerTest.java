package com.sk.skala.shopapi.global.exception;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.sk.skala.shopapi.global.auth.SessionHandler;
import com.sk.skala.shopapi.product.controller.ProductController;
import com.sk.skala.shopapi.product.service.ProductService;

/**
 * <b>계층 사이에 빠져 있던 예외 처리 경로.</b>
 * <p>
 * Phase 4를 마치고 예외·실패 분기를 전수 조사했더니, {@code GlobalExceptionHandler}의 핸들러 6개 중
 * <b>2개가 어떤 테스트에도 진입하지 않았다</b> — 미처리 예외(500)와 {@code ParameterException}(400).
 * 변이를 넣어 확인하니 <b>둘 다 테스트 119건이 전부 통과</b>했다.
 * <p>
 * 원인은 하나다. <b>계층별 테스트가 각자 자기 층만 검증하면 층 사이가 빈다.</b>
 * <ul>
 *   <li>Controller 테스트는 Service 목이 {@code ResponseException}을 던지게 했다 —
 *       그래서 그 계열 매핑은 전부 검증됐다</li>
 *   <li>도메인·Service 테스트는 {@code ParameterException}이 <b>발생하는지까지만</b> 봤다 —
 *       그것이 HTTP 상태로 무엇이 되는지는 아무도 보지 않았다</li>
 *   <li>미처리 예외는 <b>발생시키는 테스트 자체가 없었다</b></li>
 * </ul>
 * 이 클래스는 그 틈을 메운다. 대상 컨트롤러로 {@code ProductController}를 쓰는 이유는
 * 인증이 없어 예외 매핑만 남기 때문이다 (DECISIONS.md 18절).
 */
@WebMvcTest(ProductController.class)
class GlobalExceptionHandlerTest {

	/** {@code UUID}의 앞 8자리 — 16진수 8글자 */
	private static final String TRACE_ID_PATTERN =
			"^internal server error \\(traceId: [0-9a-f]{8}\\)$";

	@Autowired private MockMvc mockMvc;

	@MockBean private ProductService productService;
	@MockBean private SessionHandler sessionHandler;

	@Test
	@DisplayName("미처리 예외는 500이고 응답에 예외 메시지가 실리지 않는다")
	void 미처리_예외는_내부_정보를_유출하지_않는다() throws Exception {
		// 실제 유출 사고의 모양을 흉내낸다 — 예외 메시지에 테이블명·쿼리·파일 경로가 섞이는 경우
		String leaky = "could not execute statement [ERROR: relation \"customers\" does not exist] "
				+ "at /Users/dev/app/src/main/java/Repo.java";
		given(productService.getProductById(any())).willThrow(new RuntimeException(leaky));

		mockMvc.perform(get("/api/products/1"))
				.andExpect(status().isInternalServerError())
				// ① 예외 메시지의 어떤 조각도 응답에 나오지 않는다
				.andExpect(content().string(Matchers.not(Matchers.containsString("customers"))))
				.andExpect(content().string(Matchers.not(Matchers.containsString("relation"))))
				.andExpect(content().string(Matchers.not(Matchers.containsString("Repo.java"))))
				.andExpect(content().string(Matchers.not(Matchers.containsString("/Users/"))))
				// ② 스택트레이스 흔적도 없다
				.andExpect(content().string(Matchers.not(Matchers.containsString("java.lang."))))
				.andExpect(content().string(Matchers.not(Matchers.containsString("\tat "))))
				// ③ 대신 추적 가능한 traceId가 있다 — 로그와 대조할 수 있어야 사고 대응이 된다
				.andExpect(jsonPath("$.result").value("fail"))
				.andExpect(jsonPath("$.message").value(Matchers.matchesRegex(TRACE_ID_PATTERN)))
				.andExpect(jsonPath("$.body").doesNotExist());
	}

	@Test
	@DisplayName("traceId는 요청마다 달라야 로그에서 특정 사고를 찾을 수 있다")
	void traceId는_요청마다_다르다() throws Exception {
		given(productService.getProductById(any())).willThrow(new RuntimeException("boom"));

		String first = messageOf(mockMvc.perform(get("/api/products/1"))
				.andExpect(status().isInternalServerError()).andReturn().getResponse().getContentAsString());
		String second = messageOf(mockMvc.perform(get("/api/products/2"))
				.andExpect(status().isInternalServerError()).andReturn().getResponse().getContentAsString());

		org.assertj.core.api.Assertions.assertThat(first)
				.as("고정 문자열이면 여러 사고를 로그에서 구별할 수 없다")
				.isNotEqualTo(second);
	}

	@Test
	@DisplayName("ParameterException은 500이 아니라 400이다")
	void ParameterException은_400으로_나간다() throws Exception {
		// 도메인·Service 테스트는 '이 예외가 발생한다'까지만 확인했다.
		// 클라이언트 잘못이 500으로 나가면 재시도해도 될지 알 수 없다
		willThrow(new ParameterException("id")).given(productService).updateProduct(any());

		mockMvc.perform(put("/api/products").contentType(MediaType.APPLICATION_JSON)
						.content("{\"productName\":\"무선마우스\",\"productPrice\":15000}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.result").value("fail"))
				// Bean Validation 실패와 같은 형식이다 — 클라이언트가 두 경로를 구분할 필요가 없다
				.andExpect(jsonPath("$.message").value("invalid parameter: id"));
	}

	@Test
	@DisplayName("ParameterException 메시지에 여러 필드가 담겨도 형식이 유지된다")
	void ParameterException_메시지_형식() throws Exception {
		willThrow(new ParameterException("productName, productPrice"))
				.given(productService).updateProduct(any());

		mockMvc.perform(put("/api/products").contentType(MediaType.APPLICATION_JSON)
						.content("{\"id\":1,\"productName\":\"무선마우스\",\"productPrice\":15000}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("invalid parameter: productName, productPrice"));
	}

	// ── 스프링 MVC 요청 오류 — 클라이언트 잘못이 500으로 나가지 않아야 한다 ──────
	//
	// Phase 5에서 Actuator를 붙이며 닫힌 엔드포인트를 두드려보다 발견했다.
	// Phase 2에서 @ExceptionHandler(Exception.class)를 넣은 이후 줄곧 500이었는데,
	// 테스트가 전부 **실재하는 경로만** 호출해서 드러나지 않았다.

	@Test
	@DisplayName("없는 엔드포인트는 404다 (500이 아니다)")
	void 없는_엔드포인트는_404() throws Exception {
		mockMvc.perform(get("/api/products/list/does-not-exist"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("no such endpoint"))
				// 요청 경로를 응답에 되비추지 않는다 — 입력 반사 경로를 만들지 않는다
				.andExpect(content().string(Matchers.not(Matchers.containsString("does-not-exist"))));
	}

	@Test
	@DisplayName("지원하지 않는 메서드는 405이고 Allow 헤더를 준다")
	void 지원하지_않는_메서드는_405() throws Exception {
		mockMvc.perform(patch("/api/products"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.message").value("method not allowed"))
				// 405의 규격이 요구한다 — 클라이언트가 무엇을 써야 하는지 알 수 있어야 한다
				.andExpect(header().exists("Allow"));
	}

	@Test
	@DisplayName("Content-Type이 맞지 않으면 415다")
	void 잘못된_ContentType은_415() throws Exception {
		mockMvc.perform(post("/api/products").contentType(MediaType.TEXT_PLAIN).content("x"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.message").value("unsupported media type"));
	}

	@Test
	@DisplayName("경로 변수 타입이 맞지 않으면 400이다")
	void 경로_변수_타입_불일치는_400() throws Exception {
		mockMvc.perform(get("/api/products/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("invalid parameter type"))
				// 들어온 값을 그대로 되돌려주지 않는다
				.andExpect(content().string(Matchers.not(Matchers.containsString("abc"))));
	}

	private static String messageOf(String body) {
		int i = body.indexOf("\"message\":\"") + 11;
		return body.substring(i, body.indexOf('"', i));
	}
}
