package com.sk.skala.shopapi.global.auth;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sk.skala.shopapi.global.exception.Error;
import com.sk.skala.shopapi.global.exception.ResponseException;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * JWT 발급·검증과 쿠키 입출력.
 * Service가 이 클래스를 직접 주입받는 것은 강의 자료의 구조를 따른 것이며,
 * Phase 1에서 @LoginCustomer ArgumentResolver로 웹 계층에 격리한다 (DECISIONS.md 5절).
 */
@Component
@RequiredArgsConstructor
public class SessionHandler {

	public static final String COOKIE_NAME = "bff-access";

	// 웹 요청/응답 컨텍스트가 Service까지 끌려들어오는 지점 — Phase 1에서 제거된다.
	// 싱글턴에 주입되지만 Spring이 요청 스코프 프록시로 바꿔준다
	private final HttpServletRequest request;
	private final HttpServletResponse response;

	@Value("${jwt.secret}")
	private String secret;

	@Value("${jwt.expiration-ms}")
	private long expirationMs;

	private SecretKey key;

	/**
	 * 키를 기동 시점에 한 번 만든다.
	 * <p>
	 * 매 요청마다 만들던 때는 시크릿이 너무 짧아도 <b>앱이 정상적으로 떴다.</b>
	 * {@code WeakKeyException}은 첫 로그인에서야 터졌고, 그것도 500 "internal server error"로
	 * 나가 원인이 보이지 않았다. 설정 오류가 런타임까지 숨어 있는 셈이다.
	 * 여기서 만들면 잘못된 시크릿으로는 <b>기동 자체가 실패한다</b> (DECISIONS.md 7절 —
	 * 실패하려면 시끄럽게 실패해야 한다).
	 */
	@PostConstruct
	void initKey() {
		// HS256은 최소 256비트(32바이트)를 요구한다. 짧으면 여기서 WeakKeyException
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	/** 액세스 토큰을 발급해 응답 쿠키에 실는다. */
	public void storeAccessToken(String customerId) {
		Cookie cookie = new Cookie(COOKIE_NAME, createToken(customerId));
		cookie.setPath("/");
		cookie.setMaxAge((int) (expirationMs / 1000));
		// HttpOnly·Secure·SameSite는 Phase 2 JWT 하드닝에서 붙인다
		response.addCookie(cookie);
	}

	/** 쿠키에서 로그인한 고객 ID를 꺼낸다. 없거나 무효하면 NOT_AUTHENTICATED. */
	public String getCustomerId() {
		String token = readToken();
		if (token == null) {
			throw new ResponseException(Error.NOT_AUTHENTICATED, "no access token");
		}
		try {
			return Jwts.parser()
					.verifyWith(key)
					.build()
					.parseSignedClaims(token)
					.getPayload()
					.getSubject();
		} catch (JwtException e) {
			throw new ResponseException(Error.NOT_AUTHENTICATED, "invalid access token");
		}
	}

	private String createToken(String customerId) {
		Date now = new Date();
		return Jwts.builder()
				.subject(customerId)
				.issuedAt(now)
				.expiration(new Date(now.getTime() + expirationMs))
				.signWith(key)
				.compact();
	}

	private String readToken() {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (COOKIE_NAME.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

}
