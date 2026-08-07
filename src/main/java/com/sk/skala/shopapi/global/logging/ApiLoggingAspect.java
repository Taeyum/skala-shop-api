package com.sk.skala.shopapi.global.logging;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Controller 진입·종료와 실행시간을 남긴다.
 * <p>
 * <b>비밀번호는 절대 로그에 나가지 않는다.</b> 인자를 그대로 찍으면 가입·로그인 요청의
 * 평문 비밀번호가 파일에 남는다 — 응답으로 못 나가게 막아둔 값이 로그로 새는 셈이다
 * ({@code SensitiveDataMasker}, DECISIONS.md 20절).
 * <p>
 * <b>왜 Controller에만 거는가</b> — Service까지 걸면 같은 요청이 두 번 찍히고,
 * 도메인 간 호출({@code OrderService → CustomerService})까지 잡혀 로그가 요청 수의 몇 배가 된다.
 * 요청 하나에 로그 한 쌍이라는 대응이 유지돼야 읽을 수 있다.
 * <p>
 * <b>프록시 기반이라 자기 호출에는 걸리지 않는다.</b> 컨트롤러 메서드가 같은 클래스의
 * 다른 메서드를 부르면 그 호출은 로깅되지 않는다 — 여기서는 의도한 동작이다.
 * <p>
 * <b>한계 — Bean Validation({@code @Valid}) 실패는 여기 남지 않는다.</b>
 * 검증은 인자 바인딩 단계에서 일어나 <b>컨트롤러 메서드가 호출되기 전에</b> 터지고,
 * 메서드 실행을 감싸는 어드바이스는 그것을 볼 수 없다. 인증 실패({@code @LoginCustomer}
 * 리졸버)도 같은 이유로 보이지 않는다.
 * 두 경우 모두 {@code GlobalExceptionHandler}의 debug 로그가 기록을 담당한다.
 * 요청 단위로 빠짐없이 남겨야 한다면 필터·인터셉터로 옮겨야 한다 (DECISIONS.md 20절).
 */
@Aspect
@Component
@RequiredArgsConstructor
public class ApiLoggingAspect {

	/**
	 * 요청 바디를 JSON으로 남기기 위해 <b>스프링이 쓰는 것과 같은 ObjectMapper</b>를 주입받는다.
	 * 같은 직렬화 규칙을 타야 {@code @JsonProperty(WRITE_ONLY)} 같은 설정이 로그에도 그대로 적용된다.
	 * 별도 인스턴스를 만들면 설정이 갈라져 <b>응답에는 안 나가는 값이 로그에는 나가는</b> 상황이 생긴다.
	 */
	private final ObjectMapper objectMapper;

	private static final Logger log = LoggerFactory.getLogger(ApiLoggingAspect.class);

	/** 인자 로그가 길어지면 로그 파일만 키우고 읽히지 않는다 */
	private static final int MAX_ARG_LENGTH = 300;

	@Pointcut("within(com.sk.skala.shopapi..controller..*)")
	public void controllerLayer() {
	}

	@Around("controllerLayer()")
	public Object logApi(ProceedingJoinPoint joinPoint) throws Throwable {
		String method = joinPoint.getSignature().getDeclaringType().getSimpleName()
				+ "." + joinPoint.getSignature().getName();
		// traceId는 MDC에 있으므로 로그 패턴이 자동으로 붙인다 — 여기서 다시 찍지 않는다
		log.info("→ {} {}", method, maskedArgs(joinPoint));

		long began = System.nanoTime();
		try {
			Object result = joinPoint.proceed();
			log.info("← {} ({}ms)", method, elapsedMs(began));
			return result;
		} catch (Throwable e) {
			// 실패도 실행시간과 함께 남긴다 — 느린 실패(타임아웃)와 빠른 실패(검증)는 원인이 다르다.
			// 예외의 상세는 GlobalExceptionHandler가 책임진다. 여기서는 종류만 남긴다
			log.info("← {} 실패 {} ({}ms)", method, e.getClass().getSimpleName(), elapsedMs(began));
			throw e;
		}
	}

	private static long elapsedMs(long beganNanos) {
		return (System.nanoTime() - beganNanos) / 1_000_000;
	}

	private String maskedArgs(ProceedingJoinPoint joinPoint) {
		Object[] args = joinPoint.getArgs();
		if (args.length == 0) {
			return "()";
		}
		String joined = Arrays.stream(args)
				.map(this::describe)
				.collect(Collectors.joining(", ", "(", ")"));
		String masked = SensitiveDataMasker.mask(joined);
		return masked.length() <= MAX_ARG_LENGTH
				? masked
				: masked.substring(0, MAX_ARG_LENGTH) + "...(생략)";
	}

	/**
	 * 인자를 문자열로 만든다.
	 * <p>
	 * {@code toString()}이 예외를 던지면 <b>로깅 때문에 요청이 실패한다.</b>
	 * 로그는 부가 기능이므로 어떤 경우에도 본래 흐름을 깨뜨리면 안 된다.
	 */
	private String describe(Object arg) {
		if (arg == null) {
			return "null";
		}
		if (arg instanceof CharSequence || arg instanceof Number || arg instanceof Boolean) {
			return String.valueOf(arg);
		}
		try {
			// DTO는 JSON으로 남긴다. toString()에 기대면 @ToString이 없는 DTO는
			// CustomerRequest@327b3e8b 처럼 찍혀 로그가 아무 정보도 주지 못한다 — 실제로 그랬다
			return objectMapper.writeValueAsString(arg);
		} catch (Exception e) {
			// 직렬화 실패가 요청을 깨뜨리면 안 된다. 로그는 부가 기능이다
			return arg.getClass().getSimpleName() + "(직렬화 불가)";
		}
	}
}
