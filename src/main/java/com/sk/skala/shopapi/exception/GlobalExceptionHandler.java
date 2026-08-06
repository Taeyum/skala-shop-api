package com.sk.skala.shopapi.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.sk.skala.shopapi.common.Response;

/**
 * Phase 0은 강의 자료의 구조를 따라 비즈니스 예외를 200 + fail 바디로 돌려준다.
 * SPEC.md 4절의 Error → HTTP 상태 매핑(404/409/400/401/403)은 Phase 2에서 붙인다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	// 인증 실패도 Error.NOT_AUTHENTICATED를 담은 ResponseException으로 들어온다
	@ExceptionHandler(ResponseException.class)
	public Response<Void> handleResponse(ResponseException e) {
		return Response.fail(e.getError().name());
	}

	@ExceptionHandler(ParameterException.class)
	public Response<Void> handleParameter(ParameterException e) {
		return Response.fail(e.getMessage());
	}

	@ExceptionHandler(Exception.class)
	public Response<Void> handleUnexpected(Exception e) {
		// 스택트레이스는 로그로만. 응답에 traceId를 싣는 것은 Phase 2
		log.error("unexpected error", e);
		return Response.fail("internal server error");
	}
}
