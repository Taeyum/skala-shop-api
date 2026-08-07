package com.sk.skala.shopapi.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

	/**
	 * BCrypt는 솔트를 해시 문자열 안에 포함하므로 솔트를 따로 저장·관리할 필요가 없다.
	 * 같은 비밀번호도 매번 다른 해시가 나오므로 레인보우 테이블·해시 비교 공격이 통하지 않는다.
	 * <p>
	 * 강도(work factor)는 기본값 10을 쓴다. 올리면 대입 공격 비용이 올라가지만 로그인 지연도
	 * 함께 커지므로, 근거 없이 숫자를 바꾸지 않는다 — 조정한다면 Phase 6에서 로그인 응답시간을
	 * 측정한 뒤에 한다.
	 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
