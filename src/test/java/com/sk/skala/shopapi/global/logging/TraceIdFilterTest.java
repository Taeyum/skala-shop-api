package com.sk.skala.shopapi.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

class TraceIdFilterTest {

	private final TraceIdFilter filter = new TraceIdFilter();

	@AfterEach
	void 정리() {
		MDC.clear();
	}

	@Test
	@DisplayName("요청마다 traceId를 만들어 MDC와 응답 헤더에 넣는다")
	void traceId를_생성해_MDC와_헤더에_넣는다() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		String[] seen = new String[1];

		filter.doFilter(request, response, (req, res) -> seen[0] = TraceIdFilter.current());

		assertThat(seen[0]).as("체인 실행 중에는 MDC에 값이 있어야 한다").isNotNull().hasSize(8);
		assertThat(response.getHeader(TraceIdFilter.HEADER))
				.as("사용자가 알려준 값으로 로그를 찾으려면 응답에도 있어야 한다")
				.isEqualTo(seen[0]);
	}

	@Test
	@DisplayName("★ 요청이 끝나면 MDC를 비운다 — 톰캣은 스레드를 재사용한다")
	void 요청이_끝나면_MDC를_비운다() throws Exception {
		// 지우지 않으면 다음 요청의 로그에 이전 요청의 traceId가 붙는다.
		// 로그가 틀린 것보다 나쁘다 — 틀렸다는 것을 알 수 없기 때문이다
		filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
				new MockFilterChain());

		assertThat(TraceIdFilter.current()).isNull();
	}

	@Test
	@DisplayName("체인에서 예외가 나도 MDC를 비운다")
	void 예외가_나도_MDC를_비운다() {
		FilterChain exploding = (req, res) -> {
			throw new IllegalStateException("boom");
		};

		assertThatThrownBy(() -> filter.doFilter(
				new MockHttpServletRequest(), new MockHttpServletResponse(), exploding))
				.isInstanceOf(IllegalStateException.class);

		assertThat(TraceIdFilter.current()).as("finally가 없으면 여기서 값이 남는다").isNull();
	}

	@Test
	@DisplayName("앞단이 부여한 traceId가 있으면 이어받는다")
	void 상위에서_받은_traceId를_이어받는다() throws Exception {
		// 인스턴스가 여럿이면 같은 요청의 로그가 흩어진다. 하나의 값으로 꿰어야 추적이 된다
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(TraceIdFilter.HEADER, "gateway1");
		MockHttpServletResponse response = new MockHttpServletResponse();
		String[] seen = new String[1];

		filter.doFilter(request, response, (req, res) -> seen[0] = TraceIdFilter.current());

		assertThat(seen[0]).isEqualTo("gateway1");
		assertThat(response.getHeader(TraceIdFilter.HEADER)).isEqualTo("gateway1");
	}

	@Test
	@DisplayName("빈 헤더가 오면 새로 만든다")
	void 빈_헤더는_무시하고_새로_만든다() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(TraceIdFilter.HEADER, "   ");
		MockHttpServletResponse response = new MockHttpServletResponse();
		String[] seen = new String[1];

		filter.doFilter(request, response, (req, res) -> seen[0] = TraceIdFilter.current());

		assertThat(seen[0]).isNotBlank().hasSize(8);
	}
}
