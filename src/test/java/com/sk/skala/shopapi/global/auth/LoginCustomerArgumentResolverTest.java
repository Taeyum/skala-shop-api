package com.sk.skala.shopapi.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;

import com.sk.skala.shopapi.global.exception.Error;
import com.sk.skala.shopapi.global.exception.ResponseException;

/**
 * {@code supportsParameter}의 <b>타입 검사</b>는 어떤 테스트에서도 실행되지 않았다.
 * 그 줄을 지우는 변이가 테스트 119건을 전부 통과했다 —
 * 컨트롤러의 {@code @LoginCustomer} 파라미터가 모두 {@code String}이라 차이가 드러나지 않았기 때문이다.
 * <p>
 * 이 검사가 없으면 누군가 {@code @LoginCustomer Customer customer}처럼 쓸 때
 * <b>리졸버가 String을 반환해 타입 불일치가 런타임에 터진다.</b> 검사가 있으면 리졸버가 손을 떼고
 * Spring이 "지원하지 않는 파라미터"로 명확히 실패한다.
 */
@ExtendWith(MockitoExtension.class)
class LoginCustomerArgumentResolverTest {

	@Mock private SessionHandler sessionHandler;
	@InjectMocks private LoginCustomerArgumentResolver resolver;

	/** 리플렉션으로 파라미터를 집기 위한 픽스처 — 실제 컨트롤러 시그니처를 흉내낸다 */
	@SuppressWarnings("unused")
	static class Fixture {
		void 문자열_파라미터(@LoginCustomer String customerId) { }
		void 어노테이션_없는_문자열(String customerId) { }
		void 문자열이_아닌_파라미터(@LoginCustomer Long customerId) { }
	}

	private MethodParameter parameterOf(String methodName) throws Exception {
		for (Method method : Fixture.class.getDeclaredMethods()) {
			if (method.getName().equals(methodName)) {
				return new MethodParameter(method, 0);
			}
		}
		throw new IllegalArgumentException(methodName);
	}

	@Test
	@DisplayName("@LoginCustomer가 붙은 String 파라미터만 지원한다")
	void 어노테이션과_타입이_모두_맞아야_지원한다() throws Exception {
		assertThat(resolver.supportsParameter(parameterOf("문자열_파라미터"))).isTrue();
	}

	@Test
	@DisplayName("어노테이션이 없으면 지원하지 않는다")
	void 어노테이션이_없으면_지원하지_않는다() throws Exception {
		assertThat(resolver.supportsParameter(parameterOf("어노테이션_없는_문자열"))).isFalse();
	}

	@Test
	@DisplayName("★ @LoginCustomer가 붙어도 String이 아니면 지원하지 않는다")
	void 문자열이_아니면_지원하지_않는다() throws Exception {
		// 이 단언이 없어서 타입 검사를 지워도 아무도 몰랐다.
		// 지원한다고 답하면 리졸버가 String을 반환하고 Long 파라미터에 들어가 런타임에 터진다
		assertThat(resolver.supportsParameter(parameterOf("문자열이_아닌_파라미터"))).isFalse();
	}

	@Test
	@DisplayName("인증 실패는 리졸버에서 던진다 — Service까지 내려가지 않는다")
	void 인증_실패는_여기서_끝난다() throws Exception {
		willThrow(new ResponseException(Error.NOT_AUTHENTICATED)).given(sessionHandler).getCustomerId();

		assertThatThrownBy(() -> resolver.resolveArgument(
				parameterOf("문자열_파라미터"), null, null, null))
				.isInstanceOf(ResponseException.class)
				.extracting(e -> ((ResponseException) e).getError())
				.isEqualTo(Error.NOT_AUTHENTICATED);
	}

	@Test
	@DisplayName("인증되면 쿠키에서 꺼낸 customerId를 그대로 넘긴다")
	void 인증되면_customerId를_넘긴다() throws Exception {
		given(sessionHandler.getCustomerId()).willReturn("skala01");

		assertThat(resolver.resolveArgument(parameterOf("문자열_파라미터"), null, null, null))
				.isEqualTo("skala01");
	}
}
