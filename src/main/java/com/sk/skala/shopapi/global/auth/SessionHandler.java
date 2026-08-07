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
					.verifyWith(key())
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
				.signWith(key())
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

	private SecretKey key() {
		return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}
}
