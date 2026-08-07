package com.sk.skala.shopapi.common.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인한 고객의 {@code customerId}를 Controller 파라미터로 주입받는다.
 * <p>
 * 이 어노테이션이 붙은 엔드포인트는 인증이 필수다. 쿠키가 없거나 토큰이 무효하면
 * {@link LoginCustomerArgumentResolver}가 {@code NOT_AUTHENTICATED}를 던지므로
 * Service까지 내려가지 않는다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoginCustomer {
}
