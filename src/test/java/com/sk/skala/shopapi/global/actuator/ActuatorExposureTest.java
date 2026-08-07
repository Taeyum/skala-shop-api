package com.sk.skala.shopapi.global.actuator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.sk.skala.shopapi.support.PostgresTestContainer;

/**
 * <b>Actuator 노출 범위를 테스트로 못 박는다.</b>
 * <p>
 * {@code include: "*"} 한 글자면 {@code /actuator/env}가 열리고 거기에
 * {@code JWT_SECRET}·DB 비밀번호가 그대로 보인다. {@code /actuator/heapdump}는
 * 힙 전체를 파일로 내려준다 — 메모리에 있던 평문 비밀번호와 토큰이 함께 나간다.
 * <b>Phase 2에서 막은 것과 같은 자산이고, 설정 한 줄로 되돌아간다.</b>
 * <p>
 * 그래서 "열려 있어야 할 것"뿐 아니라 <b>"닫혀 있어야 할 것"을 이름으로 단언한다.</b>
 * 화이트리스트가 실수로 넓어지면 여기서 깨진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestContainer.class)
class ActuatorExposureTest {

	@Autowired private MockMvc mockMvc;

	@Test
	@DisplayName("health / readiness / liveness 는 열려 있다")
	void 상태_확인_엔드포인트는_열려_있다() throws Exception {
		// 컨테이너 오케스트레이터가 이것을 본다. 막으면 배포가 동작하지 않는다
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
		mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
		mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
	}

	@Test
	@DisplayName("health 응답에 내부 상세가 실리지 않는다")
	void health는_상태만_알려준다() throws Exception {
		// show-details: never. 상세를 켜면 DB 접속 URL·드라이버·디스크 경로가 나간다
		mockMvc.perform(get("/actuator/health"))
				.andExpect(jsonPath("$.components").doesNotExist())
				.andExpect(content().string(Matchers.not(Matchers.containsString("jdbc:"))))
				.andExpect(content().string(Matchers.not(Matchers.containsString("postgres"))));
	}

	@Test
	@DisplayName("★ 설정·환경·힙덤프는 닫혀 있다")
	void 정보_노출_엔드포인트는_닫혀_있다() throws Exception {
		// 이름으로 하나씩 단언한다 — include를 넓히는 실수가 여기서 잡힌다
		for (String endpoint : new String[] {
				"env", "configprops", "beans", "mappings", "loggers",
				"heapdump", "threaddump", "metrics", "info", "conditions", "scheduledtasks" }) {
			mockMvc.perform(get("/actuator/" + endpoint))
					.andExpect(status().isNotFound());
		}
	}

	@Test
	@DisplayName("actuator 목록 자체도 health 외에는 보여주지 않는다")
	void 엔드포인트_목록에_health만_보인다() throws Exception {
		mockMvc.perform(get("/actuator"))
				.andExpect(content().string(Matchers.not(Matchers.containsString("\"env\""))))
				.andExpect(content().string(Matchers.not(Matchers.containsString("\"heapdump\""))));
	}
}
