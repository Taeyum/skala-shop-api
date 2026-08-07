package com.sk.skala.shopapi.global.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
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

	private static final Logger log = LoggerFactory.getLogger(SessionHandler.class);

	public static final String COOKIE_NAME = "bff-access";

	// 웹 요청/응답 컨텍스트가 Service까지 끌려들어오는 지점 — Phase 1에서 제거된다.
	// 싱글턴에 주입되지만 Spring이 요청 스코프 프록시로 바꿔준다
	private final HttpServletRequest request;
	private final HttpServletResponse response;

	@Value("${jwt.secret}")
	private String secret;

	@Value("${jwt.expiration-ms}")
	private long expirationMs;

	@Value("${jwt.cookie.secure}")
	private boolean cookieSecure;

	@Value("${jwt.cookie.same-site}")
	private String cookieSameSite;

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

		// 쿠키 정책을 기동 로그에 남긴다 — Secure가 꺼진 사실이 조용히 넘어가지 않게 한다.
		// 설정 파일을 열어봐야만 알 수 있으면 운영에서 확인을 건너뛰게 된다
		log.info("쿠키 정책 — HttpOnly=true, Secure={}, SameSite={}, Path=/, Max-Age={}s",
				cookieSecure, cookieSameSite, expirationMs / 1000);
		if (!cookieSecure) {
			log.warn("Secure=false — HTTP 환경 전용이다. TLS 뒤에 배포하면 COOKIE_SECURE=true로 켠다");
		}
	}

	/**
	 * 액세스 토큰을 발급해 응답 쿠키에 실는다.
	 * <p>
	 * {@code jakarta.servlet.http.Cookie}는 SameSite를 지원하지 않아 {@link ResponseCookie}로 만든다.
	 * <ul>
	 *   <li><b>HttpOnly</b> — 자바스크립트가 읽지 못한다. XSS로 스크립트가 주입돼도 토큰은 못 빼간다</li>
	 *   <li><b>SameSite</b> — 교차 사이트 요청에 쿠키가 실리지 않는다 (CSRF)</li>
	 *   <li><b>Secure</b> — HTTPS에서만 전송. 현재 환경이 HTTP라 기본 false다 (DECISIONS.md 9-2절)</li>
	 * </ul>
	 */
	public void storeAccessToken(String customerId) {
		ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, createToken(customerId))
				.httpOnly(true)
				.secure(cookieSecure)
				.sameSite(cookieSameSite)
				.path("/")
				.maxAge(Duration.ofMillis(expirationMs))
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
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
