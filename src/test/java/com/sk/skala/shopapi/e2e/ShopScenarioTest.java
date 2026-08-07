package com.sk.skala.shopapi.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.sk.skala.shopapi.global.auth.SessionHandler;
import com.sk.skala.shopapi.product.dto.ProductRequest;
import com.sk.skala.shopapi.product.entity.Product;
import com.sk.skala.shopapi.product.repository.ProductRepository;
import com.sk.skala.shopapi.support.PostgresTestContainer;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * SPEC.md 5절 E2E 시나리오를 자동화 테스트 하나로 옮긴 것.
 * <p>
 * 목이 하나도 없다 — 실제 DB, 실제 JWT, 실제 필터·리졸버를 통과한다.
 * 계층별 테스트는 각 조각이 맞는지 보고, 이 테스트는 <b>조각들이 이어지는지</b>를 본다.
 * <p>
 * 시나리오는 <b>순서에 의존한다</b>(가입 → 로그인 → 주문 → 조회 → 취소). 일반적으로 테스트는
 * 순서에 의존하지 않아야 하지만, 여기서는 <b>흐름 자체가 검증 대상</b>이라 예외로 둔다.
 * 대신 픽스처를 이 클래스 안에서만 만들고 다른 테스트와 공유하지 않는다.
 * <p>
 * 시드({@code data.sql})에 의존하지 않고 상품을 직접 만든다 — 테스트 프로파일은
 * {@code sql.init.mode: never}이고, 시드에 기대면 실행 환경에 따라 건수 단언이 깨진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestContainer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShopScenarioTest {

	private static final String CUSTOMER_ID = "skala01-e2e";
	private static final String PASSWORD = "pw1234";

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private ProductRepository productRepository;

	private Cookie accessCookie;
	private Long mouseId;

	private String credentials() {
		return "{\"customerId\":\"" + CUSTOMER_ID + "\",\"customerPassword\":\"" + PASSWORD + "\"}";
	}

	@Test
	@Order(1)
	@DisplayName("1) 회원가입 — 포인트 1,000,000이 서버에서 부여된다")
	void 회원가입() throws Exception {
		mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
						.content(credentials()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.customerPoint").value(1000000.00))
				// 비밀번호는 어떤 응답에도 실리지 않는다
				.andExpect(jsonPath("$.body.customerPassword").doesNotExist());
	}

	@Test
	@Order(2)
	@DisplayName("2) 로그인 — JWT 쿠키를 받는다")
	void 로그인() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/customers/login")
						.contentType(MediaType.APPLICATION_JSON).content(credentials()))
				.andExpect(status().isOk())
				.andReturn();

		accessCookie = result.getResponse().getCookie(SessionHandler.COOKIE_NAME);
		assertThat(accessCookie).as("bff-access 쿠키가 발급돼야 한다").isNotNull();
		assertThat(accessCookie.isHttpOnly()).as("HttpOnly — 자바스크립트가 읽지 못한다").isTrue();
		assertThat(result.getResponse().getHeader("Set-Cookie"))
				.as("SameSite는 ResponseCookie로만 설정할 수 있다").contains("SameSite=Strict");
	}

	@Test
	@Order(3)
	@DisplayName("3) 상품 목록 — 등록한 3종이 보인다")
	void 상품_목록() throws Exception {
		mouseId = productRepository.save(Product.of("무선마우스", new BigDecimal("15000.00"))).getId();
		productRepository.save(Product.of("블루투스키보드", new BigDecimal("29000.00")));
		productRepository.save(Product.of("USB허브", new BigDecimal("39000.00")));

		mockMvc.perform(get("/api/products/list").param("offset", "0").param("count", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.total").value(3))
				.andExpect(jsonPath("$.body.list.length()").value(3));
	}

	@Test
	@Order(4)
	@DisplayName("4) 주문 2개 — 잔액 970,000")
	void 주문() throws Exception {
		mockMvc.perform(post("/api/customers/order").cookie(accessCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								java.util.Map.of("productId", mouseId, "quantity", 2))))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/customers/" + CUSTOMER_ID).cookie(accessCookie))
				.andExpect(jsonPath("$.body.customerPoint").value(970000.00));
	}

	@Test
	@Order(5)
	@DisplayName("5) 주문 조회 — 무선마우스 수량 2")
	void 주문_조회() throws Exception {
		mockMvc.perform(get("/api/customers/" + CUSTOMER_ID).cookie(accessCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body.products.length()").value(1))
				.andExpect(jsonPath("$.body.products[0].productName").value("무선마우스"))
				.andExpect(jsonPath("$.body.products[0].quantity").value(2));
	}

	@Test
	@Order(6)
	@DisplayName("6) 1개 취소 — 잔액 985,000 (주문 당시 가격 기준)")
	void 취소() throws Exception {
		mockMvc.perform(post("/api/customers/cancel").cookie(accessCookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								java.util.Map.of("productId", mouseId, "quantity", 1))))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/customers/" + CUSTOMER_ID).cookie(accessCookie))
				.andExpect(jsonPath("$.body.customerPoint").value(985000.00))
				.andExpect(jsonPath("$.body.products[0].quantity").value(1));
	}

	@Test
	@Order(7)
	@DisplayName("7) 쿠키 없이 조회하면 401 — 인증이 실제로 걸려 있다")
	void 미인증_접근은_거부된다() throws Exception {
		// 이 단언이 없으면 위의 모든 조회가 '인증이 없어서' 통과한 것인지 알 수 없다
		mockMvc.perform(get("/api/customers/" + CUSTOMER_ID))
				.andExpect(status().isUnauthorized());
	}
}
