package com.sk.skala.shopapi.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.sk.skala.shopapi.product.entity.Product;
import com.sk.skala.shopapi.product.repository.ProductRepository;
import com.sk.skala.shopapi.support.PostgresTestContainer;

/**
 * HTTP 캐시 헤더.
 * <p>
 * <b>가장 중요한 단언은 "개인 데이터에는 붙지 않는다"</b>이다.
 * 상품에 {@code Cache-Control: public}을 거는 것은 안전하지만, 같은 헤더가 고객·주문 응답에
 * 붙으면 중간 캐시(프록시·CDN)가 <b>남의 잔액과 주문 이력을 보관</b>하게 된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestContainer.class)
class HttpCacheTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ProductRepository productRepository;

	private Long product() {
		return productRepository.save(
				Product.of("http-cache-" + System.nanoTime(), new BigDecimal("1000.00"))).getId();
	}

	@Test
	@DisplayName("상품 상세에 ETag와 Cache-Control이 붙는다")
	void 상품_조회에_캐시_헤더가_붙는다() throws Exception {
		mockMvc.perform(get("/api/products/" + product()))
				.andExpect(status().isOk())
				.andExpect(header().exists("ETag"))
				.andExpect(header().string("Cache-Control", Matchers.containsString("max-age=60")))
				.andExpect(header().string("Cache-Control", Matchers.containsString("public")));
	}

	@Test
	@DisplayName("같은 ETag로 다시 요청하면 304이고 본문이 없다")
	void 조건부_요청은_304() throws Exception {
		Long id = product();
		MvcResult first = mockMvc.perform(get("/api/products/" + id)).andReturn();
		String etag = first.getResponse().getHeader("ETag");

		MvcResult second = mockMvc.perform(get("/api/products/" + id).header("If-None-Match", etag))
				.andExpect(status().isNotModified())
				.andReturn();

		org.assertj.core.api.Assertions.assertThat(second.getResponse().getContentLength())
				.as("304인데 본문이 실리면 아끼는 것이 없다").isLessThanOrEqualTo(0);
	}

	@Test
	@DisplayName("★ 고객·주문 응답에는 캐시 헤더가 붙지 않는다")
	void 개인_데이터에는_캐시_헤더가_없다() throws Exception {
		String id = "cache-priv-" + System.nanoTime();
		String body = "{\"customerId\":\"" + id + "\",\"customerPassword\":\"pw1234\"}";
		mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(body));
		MvcResult login = mockMvc.perform(post("/api/customers/login")
				.contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();

		mockMvc.perform(get("/api/customers/" + id)
						.cookie(login.getResponse().getCookies()))
				.andExpect(status().isOk())
				// 중간 캐시가 남의 잔액·주문 이력을 보관하게 된다
				.andExpect(header().doesNotExist("ETag"))
				.andExpect(header().doesNotExist("Cache-Control"));
	}
}
