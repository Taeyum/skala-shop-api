package com.sk.skala.shopapi.global.logging;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 요청마다 traceId를 만들어 MDC에 넣는다. 그 요청이 남기는 <b>모든 로그</b>에 같은 값이 붙는다.
 * <p>
 * 왜 필요한가 — 동시 요청이 섞이면 로그 한 줄만 보고는 어느 요청의 것인지 알 수 없다.
 * 스레드 이름({@code nio-8080-exec-3})은 재사용되므로 요청을 식별하지 못한다.
 * <p>
 * <b>응답 헤더에도 실어 보낸다.</b> 사용자가 "오류가 났다"고 신고할 때 화면의 traceId만 있으면
 * 로그에서 그 요청 전체를 끌어올 수 있다. 500 응답 바디에 traceId를 넣은 것(Phase 2 B-1)과
 * 같은 목적이고, 이제 <b>모든 응답</b>에 붙는다.
 * <p>
 * {@code finally}에서 반드시 지운다 — 톰캣은 스레드를 재사용하므로,
 * 지우지 않으면 <b>다음 요청의 로그에 이전 요청의 traceId가 붙는다.</b>
 * 로그를 믿을 수 없게 만드는 종류의 버그다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

	public static final String MDC_KEY = "traceId";
	public static final String HEADER = "X-Trace-Id";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		// 이미 앞단(게이트웨이·로드밸런서)에서 부여했다면 이어받는다 — 인스턴스가 여럿이면
		// 같은 요청이 여러 로그에 흩어지므로 하나의 값으로 꿰어야 추적이 된다 (Phase 5 HA)
		String traceId = request.getHeader(HEADER);
		if (traceId == null || traceId.isBlank()) {
			traceId = UUID.randomUUID().toString().substring(0, 8);
		}
		MDC.put(MDC_KEY, traceId);
		response.setHeader(HEADER, traceId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.remove(MDC_KEY);
		}
	}

	/** 현재 요청의 traceId. 필터 밖(스케줄러 등)에서 호출되면 null일 수 있다. */
	public static String current() {
		return MDC.get(MDC_KEY);
	}
}
