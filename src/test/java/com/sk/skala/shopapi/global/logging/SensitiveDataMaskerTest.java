package com.sk.skala.shopapi.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Spring 없이 도는 순수 단위 테스트. 마스킹은 문자열 변환이므로 컨텍스트가 필요 없다. */
class SensitiveDataMaskerTest {

	private static final String SECRET = "SuperSecret-9f3a2b";

	@Test
	@DisplayName("JSON의 비밀번호 값을 가린다")
	void JSON_비밀번호를_가린다() {
		String masked = SensitiveDataMasker.mask(
				"{\"customerId\":\"skala01\",\"customerPassword\":\"" + SECRET + "\"}");

		assertThat(masked).doesNotContain(SECRET);
		assertThat(masked).isEqualTo("{\"customerId\":\"skala01\",\"customerPassword\":\"****\"}");
		// 가릴 필요 없는 값은 그대로 남아야 로그가 쓸모 있다
		assertThat(masked).contains("skala01");
	}

	@Test
	@DisplayName("key=value 형태(toString·쿼리스트링)도 가린다")
	void toString_형태도_가린다() {
		String masked = SensitiveDataMasker.mask(
				"CustomerRequest(customerId=skala01, customerPassword=" + SECRET + ")");

		assertThat(masked).doesNotContain(SECRET);
		assertThat(masked).isEqualTo("CustomerRequest(customerId=skala01, customerPassword=****)");
	}

	@Test
	@DisplayName("대소문자와 공백이 달라도 가린다")
	void 표기_변형에_견딘다() {
		assertThat(SensitiveDataMasker.mask("{\"CUSTOMERPASSWORD\" : \"" + SECRET + "\"}"))
				.doesNotContain(SECRET);
		assertThat(SensitiveDataMasker.mask("{\"Password\":\"" + SECRET + "\"}"))
				.doesNotContain(SECRET);
		assertThat(SensitiveDataMasker.mask("pwd = " + SECRET))
				.doesNotContain(SECRET);
	}

	@Test
	@DisplayName("값 안에 따옴표가 이스케이프되어 있어도 끝까지 가린다")
	void 이스케이프된_따옴표를_넘어서_가린다() {
		// 중간에서 끊기면 뒷부분이 로그에 남는다. 정규식이 \" 를 값의 일부로 봐야 한다
		String masked = SensitiveDataMasker.mask(
				"{\"customerPassword\":\"pw\\\"" + SECRET + "\\\"tail\",\"customerId\":\"skala01\"}");

		assertThat(masked).doesNotContain(SECRET);
		assertThat(masked).contains("skala01");
	}

	@Test
	@DisplayName("토큰·시크릿도 가린다")
	void 다른_민감_필드도_가린다() {
		assertThat(SensitiveDataMasker.mask("{\"token\":\"" + SECRET + "\"}")).doesNotContain(SECRET);
		assertThat(SensitiveDataMasker.mask("{\"secret\":\"" + SECRET + "\"}")).doesNotContain(SECRET);
		assertThat(SensitiveDataMasker.mask("authorization=Bearer " + SECRET)).doesNotContain(SECRET);
	}

	@Test
	@DisplayName("민감 필드가 없으면 원문을 그대로 둔다")
	void 무관한_문자열은_건드리지_않는다() {
		String plain = "{\"productId\":1,\"quantity\":2}";

		assertThat(SensitiveDataMasker.mask(plain)).isEqualTo(plain);
		assertThat(SensitiveDataMasker.mask(null)).isNull();
		assertThat(SensitiveDataMasker.mask("")).isEmpty();
	}
}
