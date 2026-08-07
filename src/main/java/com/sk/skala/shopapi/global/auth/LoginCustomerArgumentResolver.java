package com.sk.skala.shopapi.global.auth;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import lombok.RequiredArgsConstructor;

/**
 * {@code @LoginCustomer String customerId} 파라미터를 채운다.
 * 쿠키·JWT 해석이 여기서 끝나므로 Service는 식별자만 받는다.
 */
@Component
@RequiredArgsConstructor
public class LoginCustomerArgumentResolver implements HandlerMethodArgumentResolver {

	private final SessionHandler sessionHandler;

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(LoginCustomer.class)
				&& String.class.equals(parameter.getParameterType());
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		// 인증 실패는 여기서 던진다. null을 넘기고 Service가 판단하게 하면
		// Service가 다시 '인증'이라는 웹 관심사를 아는 셈이 되어 이 단계의 목적이 무너진다
		return sessionHandler.getCustomerId();
	}
}
