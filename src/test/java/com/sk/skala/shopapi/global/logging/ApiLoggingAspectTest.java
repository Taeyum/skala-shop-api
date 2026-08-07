package com.sk.skala.shopapi.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.sk.skala.shopapi.support.PostgresTestContainer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * <b>로그를 실제로 캡처해 검증한다.</b>
 * <p>
 * 마스킹은 눈으로 한 번 확인하고 끝낼 수 있는 종류가 아니다 — DTO에 {@code @ToString}을 붙이거나
 * 필드를 하나 추가하는 것만으로 조용히 깨진다. 실제로 이 기능을 만들 때
 * <b>DTO에 {@code toString()}이 없어 마스커가 한 번도 실행되지 않은 채</b>
 * "비밀번호가 로그에 없다"는 결과가 나왔다 (JOURNAL 2026-08-07).
 * 그때의 0건은 방어의 결과가 아니라 <b>로그가 아무것도 안 남긴 결과</b>였다.
 * <p>
 * 그래서 이 테스트는 두 가지를 함께 단언한다 —
 * <b>① 비밀번호가 없다</b>, 그리고 <b>② 다른 필드는 있다</b>(로그가 실제로 내용을 남겼다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestContainer.class)
class ApiLoggingAspectTest {

	private static final String SECRET = "SuperSecret-DO-NOT-LOG-9f3a2b";

	@Autowired private MockMvc mockMvc;

	private Logger aspectLogger;
	private ListAppender<ILoggingEvent> captured;

	@BeforeEach
	void 로그_캡처_시작() {
		aspectLogger = (Logger) LoggerFactory.getLogger(ApiLoggingAspect.class);
		captured = new ListAppender<>();
		captured.start();
		aspectLogger.addAppender(captured);
		aspectLogger.setLevel(Level.INFO);
	}

	@AfterEach
	void 로그_캡처_종료() {
		aspectLogger.detachAppender(captured);
	}

	private String allLogs() {
		return captured.list.stream().map(ILoggingEvent::getFormattedMessage)
				.reduce("", (a, b) -> a + "\n" + b);
	}

	@Test
	@DisplayName("가입·로그인 로그에 평문 비밀번호가 없다 (그리고 다른 필드는 있다)")
	void 비밀번호는_로그에_남지_않는다() throws Exception {
		String customerId = "log-" + System.nanoTime();
		String body = "{\"customerId\":\"" + customerId + "\",\"customerPassword\":\"" + SECRET + "\"}";

		mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(body));
		mockMvc.perform(post("/api/customers/login").contentType(MediaType.APPLICATION_JSON).content(body));

		String logs = allLogs();
		// ① 비밀번호가 없다
		assertThat(logs).as("평문 비밀번호가 로그에 남으면 응답으로 막아둔 것이 로그로 새는 셈이다")
				.doesNotContain(SECRET);
		// ② ★ 로그가 실제로 내용을 남겼다 — 이 단언이 없으면 '아무것도 안 찍힘'도 통과한다
		assertThat(logs).as("요청 내용이 남지 않으면 로깅 자체가 무의미하다")
				.contains(customerId);
		assertThat(logs).contains("createCustomer", "loginCustomer");
	}

	@Test
	@DisplayName("실행시간과 성공·실패가 남는다")
	void 실행시간과_결과가_남는다() throws Exception {
		mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
				.content("{\"customerId\":\"log2-" + System.nanoTime() + "\",\"customerPassword\":\"pw\"}"));

		List<String> messages = captured.list.stream().map(ILoggingEvent::getFormattedMessage).toList();

		assertThat(messages).anySatisfy(m -> assertThat(m).startsWith("→"));
		assertThat(messages).anySatisfy(m -> assertThat(m).startsWith("←").contains("ms)"));
	}

	@Test
	@DisplayName("메서드 안에서 난 실패는 예외 종류와 실행시간을 남긴다")
	void 실패도_기록된다() throws Exception {
		// 느린 실패(타임아웃)와 빠른 실패(검증)는 원인이 다르다 — 시간이 함께 있어야 구별된다
		String body = "{\"customerId\":\"dup-" + System.nanoTime()
				+ "\",\"customerPassword\":\"pw\"}";
		mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(body));
		mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(body));

		assertThat(allLogs()).contains("실패").contains("ResponseException").contains("ms)");
	}

	@Test
	@DisplayName("★ 한계 — Bean Validation 실패는 AOP 로그에 남지 않는다")
	void 검증_실패는_AOP를_거치지_않는다() throws Exception {
		// @Valid는 인자 바인딩 단계에서 터지므로 컨트롤러 메서드가 아예 호출되지 않는다.
		// 메서드 실행을 감싸는 AOP로는 잡을 수 없다 — 필터·인터셉터라면 잡힌다.
		//
		// 이 테스트는 '고쳐야 할 버그'가 아니라 **현재 동작이 무엇인지 못 박는 것**이다.
		// 400 검증 실패의 기록은 GlobalExceptionHandler의 debug 로그가 담당한다.
		// 나중에 로깅을 필터로 옮기면 이 테스트가 깨지면서 동작이 바뀌었음을 알려준다
		mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
				.content("{\"customerId\":\"\",\"customerPassword\":\"\"}"));

		assertThat(allLogs()).as("AOP는 메서드 실행을 감싸므로 진입 전 실패는 보이지 않는다")
				.doesNotContain("createCustomer");
	}
}
