package com.sk.skala.shopapi.global.logging;

import java.util.regex.Pattern;

/**
 * 로그에 남기기 전에 민감한 값을 가린다.
 * <p>
 * Phase 2 자산 점검에서 비밀번호의 <b>로그 경로는 "미점검"으로 남겨두었다</b> —
 * 응답 경로(DTO·{@code @JsonIgnore})만 막았고, 그때는 로그에 요청 바디를 찍는 코드가 없었다.
 * API 로깅을 붙이는 지금이 그 경로가 실제로 생기는 시점이다.
 * <p>
 * <b>필드 이름 기준으로 가린다.</b> 값의 모양(길이·문자 구성)으로 판단하면
 * 평범한 비밀번호를 놓치고 평범한 문자열을 지운다.
 */
public final class SensitiveDataMasker {

	/** 가릴 필드 이름. 소문자로 비교한다 */
	private static final String[] SENSITIVE_FIELDS = {
			"customerpassword", "password", "passwd", "pwd", "secret", "token", "authorization",
	};

	private static final String MASK = "\"****\"";

	/**
	 * {@code "customerPassword" : "pw1234"} 형태를 찾는다.
	 * <ul>
	 *   <li>{@code (?i)} 대소문자 무시 — {@code customerPassword}와 {@code CUSTOMERPASSWORD}를 모두 잡는다</li>
	 *   <li>콜론 앞뒤 공백 허용 — 직렬화기마다 다르다</li>
	 *   <li>이스케이프된 따옴표({@code \\"})까지 값의 일부로 본다 — 중간에서 끊기면 뒷부분이 남는다</li>
	 * </ul>
	 */
	private static final Pattern JSON_FIELD = Pattern.compile(
			"(?i)(\"(?:" + String.join("|", SENSITIVE_FIELDS) + ")\"\\s*:\\s*)\"(?:\\\\.|[^\"\\\\])*\"");

	/**
	 * {@code customerPassword=pw1234} — toString()·쿼리스트링 형태.
	 * <p>
	 * <b>값에 공백을 허용한다.</b> 처음에는 공백을 값의 끝으로 봤는데,
	 * {@code authorization=Bearer eyJhbGci...} 에서 {@code Bearer}만 가려지고
	 * <b>토큰이 그대로 남았다</b> — 테스트가 잡았다. 값이 어디서 끝나는지 알 수 없으므로
	 * 구분자({@code , ; & ) ] } " 개행})까지 삼킨다.
	 * 로그에서는 <b>과하게 가리는 쪽이 안전하다</b> — 덜 가리면 그게 유출이다.
	 */
	private static final Pattern KV_FIELD = Pattern.compile(
			"(?i)((?:" + String.join("|", SENSITIVE_FIELDS) + ")\\s*=\\s*)([^,;&)\\]}\"\\n]+)");

	private SensitiveDataMasker() {
	}

	public static String mask(String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}
		String masked = JSON_FIELD.matcher(text).replaceAll("$1" + MASK);
		return KV_FIELD.matcher(masked).replaceAll("$1****");
	}
}
