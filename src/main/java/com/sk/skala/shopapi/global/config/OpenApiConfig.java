package com.sk.skala.shopapi.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * API 문서. 채점자가 Postman 없이 브라우저에서 바로 호출할 수 있어야 한다.
 * <p>
 * <b>인증은 쿠키로 동작한다.</b> Swagger UI에서 {@code POST /api/customers/login}을 실행하면
 * 브라우저가 {@code Set-Cookie}를 저장하고, 이후 요청에 자동으로 실린다 —
 * 같은 오리진이라 {@code SameSite=Strict}에도 걸리지 않는다.
 * 토큰을 복사해 붙여넣는 절차가 필요 없다.
 * <p>
 * 쿠키가 {@code HttpOnly}라 Swagger UI의 "Authorize" 버튼으로는 값을 넣을 수 없다.
 * 아래 {@code SecurityScheme}은 <b>어떤 엔드포인트가 인증을 요구하는지 문서에 표시</b>하기 위한
 * 것이지 값을 입력받기 위한 것이 아니다.
 */
@Configuration
public class OpenApiConfig {

	public static final String COOKIE_AUTH = "bff-access";

	@Bean
	public OpenAPI shopOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("SKALA-SHOP API")
						.version("v1")
						.description("""
								온라인 쇼핑몰 백엔드 REST API.

								**인증** — `POST /api/customers/login`을 먼저 실행하면 `bff-access` 쿠키가
								브라우저에 저장되고 이후 요청에 자동으로 실립니다. 별도 입력이 필요 없습니다.

								**주의** — 주문·취소·고객 조회는 본인 것만 접근할 수 있습니다(403 `NOT_OWNER`).
								"""))
				.components(new Components().addSecuritySchemes(COOKIE_AUTH,
						new SecurityScheme()
								.type(SecurityScheme.Type.APIKEY)
								.in(SecurityScheme.In.COOKIE)
								.name(COOKIE_AUTH)
								.description("로그인 시 발급되는 JWT 쿠키. HttpOnly라 여기서 값을 넣을 수 없고, "
										+ "로그인 API를 실행하면 브라우저가 자동으로 보관합니다.")));
	}
}
