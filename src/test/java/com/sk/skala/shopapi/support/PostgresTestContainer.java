package com.sk.skala.shopapi.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 테스트용 PostgreSQL. <b>운영과 같은 엔진을 쓴다</b> — 임베디드 DB로 대체하면 방언 차이를
 * 못 잡고, 쿼리 수·실행계획을 근거로 쓰는 이 Phase의 측정이 무의미해진다 (DECISIONS.md 6절).
 * <p>
 * 이미지 태그는 {@code docker-compose.yml}과 맞춘다. 다르면 테스트가 통과해도
 * 운영에서 다르게 동작할 수 있다.
 * <p>
 * {@code withReuse(true)}는 컨테이너를 테스트 간에 재사용해 기동 시간을 없앤다.
 * 개발자별 opt-in이라 {@code ~/.testcontainers.properties}에
 * {@code testcontainers.reuse.enable=true}가 없으면 조용히 무시된다 (README 참조).
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestContainer {

	@Bean
	@ServiceConnection
	PostgreSQLContainer<?> postgres() {
		return new PostgreSQLContainer<>("postgres:16-alpine").withReuse(true);
	}
}
