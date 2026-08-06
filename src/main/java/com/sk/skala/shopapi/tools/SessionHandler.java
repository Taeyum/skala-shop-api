package com.sk.skala.shopapi.tools;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sk.skala.shopapi.exception.NotAuthenticatedException;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * JWT 발급·검증과 쿠키 입출력.
 * Service가 이 클래스를 직접 주입받는 것은 스펙 구조를 따른 것이며,
 * Phase 1에서 @LoginCustomer ArgumentResolver로 웹 계층에 격리한다 (DECISIONS.md 5절).
 */
@Component
@RequiredArgsConstructor
public class SessionHandler {

	public static final String COOKIE_NAME = "bff-access";

	// 웹 요청 컨텍스트가 Service까지 끌려들어오는 지점 — Phase 1에서 제거된다
	private final HttpServletRequest request;

	@Value("${jwt.secret}")
	private String secret;

	@Value("${jwt.expiration-ms}")
	private long expirationMs;

	public String createToken(String customerId) {
		Date now = new Date();
		return Jwts.builder()
				.subject(customerId)
				.issuedAt(now)
				.expiration(new Date(now.getTime() + expirationMs))
				.signWith(key())
				.compact();
	}

	/** 쿠키에서 로그인한 고객 ID를 꺼낸다. 없거나 무효하면 NOT_AUTHENTICATED. */
	public String getCustomerId() {
		String token = readToken();
		if (token == null) {
			throw new NotAuthenticatedException();
		}
		try {
			return Jwts.parser()
					.verifyWith(key())
					.build()
					.parseSignedClaims(token)
					.getPayload()
					.getSubject();
		} catch (JwtException e) {
			throw new NotAuthenticatedException();
		}
	}

	public void writeCookie(HttpServletResponse response, String token) {
		Cookie cookie = new Cookie(COOKIE_NAME, token);
		cookie.setPath("/");
		cookie.setMaxAge((int) (expirationMs / 1000));
		// HttpOnly·Secure·SameSite는 Phase 2 JWT 하드닝에서 붙인다
		response.addCookie(cookie);
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
