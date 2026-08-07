package com.sk.skala.shopapi.global.exception;

import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.sk.skala.shopapi.global.common.Response;
import com.sk.skala.shopapi.global.logging.TraceIdFilter;

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
	 * 낙관적 락 충돌 → 409.
	 * <p>
	 * 이 핸들러가 없으면 아래 일반 핸들러로 떨어져 <b>정상적인 동시성 제어가 500으로 나간다.</b>
	 * 500은 "서버가 잘못했다"는 뜻이라 클라이언트가 재시도해도 될지 알 수 없다.
	 * 409는 <b>다시 시도하면 성공할 수 있다</b>는 정보를 준다 — 락을 거는 것과 충돌 이후를
	 * 설계하는 것은 별개의 일이다.
	 * <p>
	 * 서버가 자동 재시도({@code @Retryable})하지 않는 이유는 DECISIONS.md 14절.
	 */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ResponseEntity<Response<Void>> handleOptimisticLock(OptimisticLockingFailureException e) {
		// 충돌은 정상 동작이므로 error가 아니라 debug다. 500으로 새면 그때가 진짜 문제다
		log.debug("optimistic lock conflict: {}", e.getMessage());
		return ResponseEntity.status(Error.CONCURRENT_MODIFICATION.getStatus())
				.body(Response.fail(Error.CONCURRENT_MODIFICATION.name()));
	}

	// ── 여기부터: 스프링 MVC가 던지는 요청 오류들 ────────────────────────────
	//
	// 이 핸들러들이 없으면 전부 아래 일반 핸들러로 떨어져 **클라이언트 잘못이 500으로 나간다.**
	// Phase 5에서 Actuator를 붙이며 닫힌 엔드포인트를 두드려보다 발견했다 —
	// 없는 URL, 지원하지 않는 메서드, 잘못된 Content-Type, 경로 변수 타입 불일치가
	// 모두 500 + ERROR 로그(스택트레이스 48줄)를 만들고 있었다.
	//
	// 상태 코드가 틀린 것보다 **로그가 더 문제다.** 스캐너가 /wp-admin 같은 경로를 훑기만 해도
	// ERROR 레벨 스택트레이스가 쌓여 진짜 장애를 덮는다. 운영에서 알람이 무의미해지는 경로다.

	/** 없는 엔드포인트 → 404. 경로를 응답에 되비추지 않는다 — 로그에만 남긴다 */
	@ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
	public ResponseEntity<Response<Void>> handleNotFound(Exception e) {
		log.debug("no such endpoint: {}", e.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Response.fail("no such endpoint"));
	}

	/**
	 * 지원하지 않는 HTTP 메서드 → 405.
	 * {@code Allow} 헤더에 지원 메서드를 담는다 — 405의 규격이 요구하는 것이고,
	 * 클라이언트가 무엇을 써야 하는지 알 수 있다.
	 */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<Response<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
		log.debug("method not allowed: {}", e.getMessage());
		ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
		if (e.getSupportedHttpMethods() != null) {
			builder.allow(e.getSupportedHttpMethods().toArray(new org.springframework.http.HttpMethod[0]));
		}
		return builder.body(Response.fail("method not allowed"));
	}

	/** Content-Type이 맞지 않음 → 415 */
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<Response<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
		log.debug("unsupported media type: {}", e.getMessage());
		return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
				.body(Response.fail("unsupported media type"));
	}

	/**
	 * 경로 변수·쿼리 파라미터의 타입이 맞지 않음 → 400. ({@code /api/products/abc})
	 * <p>
	 * {@code MethodArgumentTypeMismatchException}의 상위 타입으로 받는다 —
	 * 바인딩 과정의 타입 불일치를 한자리에서 처리한다.
	 */
	@ExceptionHandler(TypeMismatchException.class)
	public ResponseEntity<Response<Void>> handleTypeMismatch(TypeMismatchException e) {
		log.debug("type mismatch: {}", e.getMessage());
		// 값을 응답에 싣지 않는다 — 입력을 그대로 되비추면 그 자체가 반사 경로가 된다
		return ResponseEntity.badRequest().body(Response.fail("invalid parameter type"));
	}

	/**
	 * DB 제약 위반 → 409.
	 * <p>
	 * <b>Phase 6 부하 측정이 찾아낸 결함이다.</b> 같은 {@code (고객, 상품)} 조합의 <b>첫 주문</b>이
	 * 동시에 들어오면 양쪽 모두 {@code findByCustomerAndProduct}에서 빈 결과를 받고 INSERT를 시도해
	 * {@code uk_order_items_customer_product} 복합 UNIQUE에 걸린다 — 전형적인 check-then-act 경합이다.
	 * <p>
	 * {@code Customer}의 {@code @Version}은 이것을 막지 못한다. 다른 행이고, 게다가
	 * {@code IDENTITY} 전략이라 {@code OrderItem} INSERT가 <b>커밋 전에 즉시 실행</b>되어
	 * 낙관적 락 검사보다 먼저 터진다.
	 * <p>
	 * 제약 위반은 <b>정의상 상태 충돌</b>이므로 500이 아니라 409다 — 클라이언트가 다시 시도하면
	 * 이번엔 기존 행을 찾아 수량 누적 경로로 간다. 서비스가 의미를 아는 경우
	 * ({@code DATA_IN_USE})는 각 Service가 먼저 잡아 자기 코드로 변환하므로 여기까지 오지 않는다.
	 * <p>
	 * Phase 3의 동시성 테스트는 스레드마다 <b>서로 다른 상품</b>을 주문해 고객 행 경합만 남겼다.
	 * 그 설계가 정확히 이 경우를 배제했고, 그래서 부하 측정 전까지 드러나지 않았다.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Response<Void>> handleConstraintViolation(DataIntegrityViolationException e) {
		// 500으로 새면 그때가 진짜 문제다. 충돌 자체는 정상 동작이므로 debug
		log.debug("constraint conflict: {}", e.getMessage());
		return ResponseEntity.status(Error.CONCURRENT_MODIFICATION.getStatus())
				.body(Response.fail(Error.CONCURRENT_MODIFICATION.name()));
	}

	/**
	 * 예상 못한 예외 → 500. 스택트레이스는 로그에만 남기고 응답에는 traceId만 내보낸다.
	 * 예외 메시지에 테이블명·쿼리·파일 경로가 섞여 나가면 그 자체가 정보 유출이다.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Response<Void>> handleUnexpected(Exception e) {
		// 필터가 MDC에 넣어둔 값을 그대로 쓴다 — 응답의 traceId와 로그의 traceId가 같아야
		// 사용자가 알려준 값으로 로그를 찾을 수 있다. 여기서 새로 만들면 둘이 어긋난다.
		// 필터 밖에서 호출되는 경우(테스트·스케줄러)를 위해 없으면 즉석에서 만든다
		String traceId = TraceIdFilter.current();
		if (traceId == null) {
			traceId = UUID.randomUUID().toString().substring(0, 8);
		}
		log.error("unexpected error [traceId={}]", traceId, e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Response.fail("internal server error (traceId: " + traceId + ")"));
	}
}
