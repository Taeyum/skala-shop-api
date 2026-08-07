package com.sk.skala.shopapi.global.exception;

import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.sk.skala.shopapi.global.common.Response;

/**
 * 예외를 SPEC.md 4절의 HTTP 상태로 매핑한다.
 * <p>
 * 응답 <b>바디</b>는 공통 {@code Response} 형태를 그대로 유지한다 — 바뀌는 것은 상태 코드뿐이다.
 * Phase 0~1에서는 모든 응답이 200이라 클라이언트가 HTTP 레벨에서 성공/실패를 구분할 수 없었다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** 비즈니스 예외 — 상태는 Error가 들고 있다. 인증 실패도 여기로 들어온다 */
	@ExceptionHandler(ResponseException.class)
	public ResponseEntity<Response<Void>> handleResponse(ResponseException e) {
		// 맥락 메시지("Customer not found")는 로그에만. 응답에는 코드만 내보낸다
		log.debug("business error: {}", e.getMessage());
		return ResponseEntity.status(e.getError().getStatus())
				.body(Response.fail(e.getError().name()));
	}

	/** 필수값 누락·형식 오류 → 400 */
	@ExceptionHandler(ParameterException.class)
	public ResponseEntity<Response<Void>> handleParameter(ParameterException e) {
		log.debug("parameter error: {}", e.getMessage());
		return ResponseEntity.badRequest().body(Response.fail(e.getMessage()));
	}

	/**
	 * Bean Validation 실패 → 400.
	 * <p>
	 * 여러 필드가 한꺼번에 걸릴 수 있으나 공통 {@code Response}의 {@code message}는 하나뿐이다.
	 * 자료의 {@code ParameterException("productName", "productPrice")} 형식을 따라
	 * <b>필드명을 쉼표로 이어</b> {@code "invalid parameter: productName, productPrice"}로 만든다.
	 * 필드명을 정렬하는 이유는 검증 순서가 보장되지 않아 같은 요청에 메시지가 달라지는 것을 막기 위해서다.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Response<Void>> handleValidation(MethodArgumentNotValidException e) {
		String fields = e.getBindingResult().getFieldErrors().stream()
				.map(FieldError::getField)
				.distinct()
				.sorted()
				.collect(Collectors.joining(", "));
		log.debug("validation error: {}", e.getMessage());
		return ResponseEntity.badRequest().body(Response.fail("invalid parameter: " + fields));
	}

	/**
	 * 바디가 JSON이 아니거나 타입이 맞지 않는 경우 → 400.
	 * <p>
	 * 이 핸들러가 없으면 아래 일반 핸들러로 떨어져 <b>클라이언트 잘못이 500으로 나간다.</b>
	 * 실제로 겪었다 — 깨진 JSON을 보냈을 때 "internal server error"가 돌아왔다.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Response<Void>> handleUnreadable(HttpMessageNotReadableException e) {
		log.debug("malformed request body: {}", e.getMessage());
		return ResponseEntity.badRequest().body(Response.fail("malformed request body"));
	}

	/**
	 * 예상 못한 예외 → 500. 스택트레이스는 로그에만 남기고 응답에는 traceId만 내보낸다.
	 * 예외 메시지에 테이블명·쿼리·파일 경로가 섞여 나가면 그 자체가 정보 유출이다.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Response<Void>> handleUnexpected(Exception e) {
		String traceId = UUID.randomUUID().toString().substring(0, 8);
		log.error("unexpected error [traceId={}]", traceId, e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Response.fail("internal server error (traceId: " + traceId + ")"));
	}
}
