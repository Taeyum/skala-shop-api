package com.sk.skala.shopapi.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import com.sk.skala.shopapi.global.exception.Error;
import com.sk.skala.shopapi.global.exception.ResponseException;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;

/**
 * <b>이 테스트가 왜 뒤늦게 추가됐는지가 이 클래스의 존재 이유다.</b>
 * <p>
 * Phase 4에서 계층 테스트를 다 짠 시점에 {@code SessionHandler}의 라인 커버리지는 <b>93%</b>였다.
 * 숫자만 보면 잘 덮인 클래스다. 그런데 <b>"서명 검증에 실패해도 페이로드의 sub를 그대로 신뢰한다"</b>는
 * 변이를 넣었더니 <b>테스트 104건 중 단 하나도 실패하지 않았다.</b>
 * <p>
 * 다른 테스트들이 <b>정상 토큰으로 로그인해 그 쿠키를 다시 쓰는 경로</b>만 밟았기 때문이다.
 * 그 경로는 서명이 항상 유효하므로 {@code catch (JwtException)} 블록에 들어갈 일이 없고,
 * 들어가지 않는 코드는 <b>어떻게 고쳐놔도 아무도 모른다.</b>
 * <p>
 * 커버리지는 "이 줄이 실행됐다"만 말한다. <b>"이 줄이 옳은지 확인됐다"고는 말하지 않는다.</b>
 * (DECISIONS.md 16절, JOURNAL 2026-08-07)
 */
class SessionHandlerTest {

	private static final String SECRET = "test-only-secret-not-used-anywhere-else-0123456789";
	private static final long EXPIRATION_MS = 3_600_000L;

	private MockHttpServletRequest request;
	private MockHttpServletResponse response;
	private SessionHandler sessionHandler;

	@BeforeEach
	void setUp() {
		request = new MockHttpServletRequest();
		response = new MockHttpServletResponse();
		sessionHandler = newHandler(SECRET, EXPIRATION_MS);
	}

	private SessionHandler newHandler(String secret, long expirationMs) {
		SessionHandler handler = new SessionHandler(request, response);
		ReflectionTestUtils.setField(handler, "secret", secret);
		ReflectionTestUtils.setField(handler, "expirationMs", expirationMs);
		ReflectionTestUtils.setField(handler, "cookieSecure", false);
		ReflectionTestUtils.setField(handler, "cookieSameSite", "Strict");
		handler.initKey();
		return handler;
	}

	/** 응답에 실린 쿠키를 다음 요청으로 넘긴다 — 브라우저가 하는 일 */
	private void carryCookieToRequest() {
		String setCookie = response.getHeader("Set-Cookie");
		String value = setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
		request.setCookies(new Cookie(SessionHandler.COOKIE_NAME, value));
	}

	@Test
	@DisplayName("발급한 토큰으로 고객 ID를 되찾는다")
	void 정상_토큰은_통과한다() {
		sessionHandler.storeAccessToken("skala01");
		carryCookieToRequest();

		assertThat(sessionHandler.getCustomerId()).isEqualTo("skala01");
	}

	@Test
	@DisplayName("★ 서명이 조작된 토큰은 거부한다 — 커버리지 93%가 가리고 있던 구멍")
	void 위조된_서명은_거부한다() {
		// 다른 키로 서명한 토큰. 페이로드는 완벽히 정상이고 sub만 남의 것이다
		SecretKey attackerKey = Keys.hmacShaKeyFor(
				"attacker-secret-key-that-is-long-enough-0123456789".getBytes(StandardCharsets.UTF_8));
		String forged = Jwts.builder()
				.subject("victim")
				.issuedAt(new Date())
				.expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
				.signWith(attackerKey)
				.compact();
		request.setCookies(new Cookie(SessionHandler.COOKIE_NAME, forged));

		assertThatThrownBy(() -> sessionHandler.getCustomerId())
				.isInstanceOf(ResponseException.class)
				.extracting(e -> ((ResponseException) e).getError())
				.isEqualTo(Error.NOT_AUTHENTICATED);
	}

	@Test
	@DisplayName("★ 서명 없이 페이로드만 바꾼 토큰도 거부한다")
	void 페이로드만_바꾼_토큰은_거부한다() {
		sessionHandler.storeAccessToken("skala01");
		carryCookieToRequest();
		String valid = request.getCookies()[0].getValue();

		// 정상 토큰의 페이로드를 남의 ID로 바꾸고 서명은 그대로 둔다
		String[] parts = valid.split("\\.");
		String tampered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
				.encodeToString("{\"sub\":\"victim\"}".getBytes(StandardCharsets.UTF_8))
				+ "." + parts[2];
		request.setCookies(new Cookie(SessionHandler.COOKIE_NAME, tampered));

		assertThatThrownBy(() -> sessionHandler.getCustomerId())
				.isInstanceOf(ResponseException.class);
	}

	@Test
	@DisplayName("만료된 토큰은 거부한다")
	void 만료된_토큰은_거부한다() {
		// 만료시간을 음수로 줘서 이미 지난 토큰을 만든다
		SessionHandler expiring = newHandler(SECRET, -1000L);
		expiring.storeAccessToken("skala01");
		carryCookieToRequest();

		assertThatThrownBy(() -> sessionHandler.getCustomerId())
				.isInstanceOf(ResponseException.class)
				.extracting(e -> ((ResponseException) e).getError())
				.isEqualTo(Error.NOT_AUTHENTICATED);
	}

	@Test
	@DisplayName("토큰이 아닌 문자열은 거부한다")
	void 쓰레기_값은_거부한다() {
		request.setCookies(new Cookie(SessionHandler.COOKIE_NAME, "not-a-jwt"));

		assertThatThrownBy(() -> sessionHandler.getCustomerId())
				.isInstanceOf(ResponseException.class);
	}

	@Test
	@DisplayName("쿠키가 아예 없거나 다른 쿠키만 있으면 거부한다")
	void 쿠키가_없으면_거부한다() {
		assertThatThrownBy(() -> sessionHandler.getCustomerId())
				.isInstanceOf(ResponseException.class)
				.extracting(e -> ((ResponseException) e).getError())
				.isEqualTo(Error.NOT_AUTHENTICATED);

		request.setCookies(new Cookie("other", "value"));
		assertThatThrownBy(() -> sessionHandler.getCustomerId())
				.isInstanceOf(ResponseException.class);
	}

	@Test
	@DisplayName("쿠키 보안 플래그가 실제로 붙는다")
	void 쿠키에_보안_플래그가_붙는다() {
		sessionHandler.storeAccessToken("skala01");

		String setCookie = response.getHeader("Set-Cookie");
		assertThat(setCookie).contains("HttpOnly");        // XSS로도 토큰을 못 빼간다
		assertThat(setCookie).contains("SameSite=Strict"); // CSRF
		assertThat(setCookie).contains("Path=/");
		assertThat(setCookie).contains("Max-Age=3600");
	}

	@Test
	@DisplayName("짧은 시크릿은 기동 시점에 거부한다 (fail-fast)")
	void 약한_시크릿은_기동을_막는다() {
		// 매 요청마다 키를 만들던 때는 앱이 정상적으로 뜨고 첫 로그인에서야 500으로 터졌다.
		// @PostConstruct로 옮겨 설정 오류가 런타임까지 숨지 않게 했다 (DECISIONS.md 9-1절)
		assertThatThrownBy(() -> newHandler("too-short", EXPIRATION_MS))
				.isInstanceOf(io.jsonwebtoken.security.WeakKeyException.class);
	}
}
