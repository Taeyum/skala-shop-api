package com.sk.skala.shopapi.tools;

/** 문자열 유틸. 강의 자료의 Service 명세가 참조하는 isAnyEmpty를 제공한다. */
public final class StringUtil {

	private StringUtil() {
	}

	/**
	 * 하나라도 null이거나 공백뿐이면 true.
	 * null 검사만으로는 {@code ""}나 {@code "   "}가 통과해 빈 상품명·빈 비밀번호가 저장된다.
	 */
	public static boolean isAnyEmpty(String... values) {
		if (values == null) {
			return true;
		}
		for (String value : values) {
			if (value == null || value.isBlank()) {
				return true;
			}
		}
		return false;
	}
}
