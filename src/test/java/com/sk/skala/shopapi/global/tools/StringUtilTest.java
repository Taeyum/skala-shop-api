package com.sk.skala.shopapi.global.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StringUtilTest {

	@Test
	void 하나라도_null이거나_공백뿐이면_true() {
		// null 검사만으로는 ""나 "   "가 통과해 빈 상품명·빈 비밀번호가 저장된다
		assertThat(StringUtil.isAnyEmpty("a", null)).isTrue();
		assertThat(StringUtil.isAnyEmpty("a", "")).isTrue();
		assertThat(StringUtil.isAnyEmpty("a", "   ")).isTrue();
		assertThat(StringUtil.isAnyEmpty("\t\n")).isTrue();
	}

	@Test
	void 전부_값이_있으면_false() {
		assertThat(StringUtil.isAnyEmpty("a", "b")).isFalse();
		assertThat(StringUtil.isAnyEmpty("skala01")).isFalse();
	}

	@Test
	void 인자_배열_자체가_null이면_true() {
		assertThat(StringUtil.isAnyEmpty((String[]) null)).isTrue();
	}
}
