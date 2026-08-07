package com.sk.skala.shopapi.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화.
 * <p>
 * {@code @SpringBootApplication}에 직접 붙이지 않고 별도 설정으로 분리한다 —
 * 붙이면 {@code @WebMvcTest} 같은 슬라이스 테스트가 <b>Auditing 인프라를 찾다 실패</b>한다.
 * 메인 클래스의 어노테이션은 모든 슬라이스가 읽기 때문이다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
