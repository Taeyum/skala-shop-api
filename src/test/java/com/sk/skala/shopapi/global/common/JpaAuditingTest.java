package com.sk.skala.shopapi.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.sk.skala.shopapi.customer.entity.Customer;
import com.sk.skala.shopapi.customer.repository.CustomerRepository;
import com.sk.skala.shopapi.global.config.JpaAuditingConfig;
import com.sk.skala.shopapi.support.PostgresTestContainer;

import jakarta.persistence.EntityManager;

/**
 * {@code @DataJpaTest}는 {@code @EnableJpaAuditing}이 붙은 설정을 자동으로 담지 않으므로
 * 명시적으로 import한다. 이것이 없으면 <b>createdAt이 null인 채 저장되고
 * {@code nullable = false} 제약에 걸려 실패</b>한다 — 감사 인프라가 빠졌음이 바로 드러난다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({ PostgresTestContainer.class, JpaAuditingConfig.class })
class JpaAuditingTest {

	@Autowired private CustomerRepository customerRepository;
	@Autowired private EntityManager entityManager;

	private Customer saved() {
		return customerRepository.save(
				Customer.register("audit-" + System.nanoTime(), "pw", new BigDecimal("1000000.00")));
	}

	@Test
	@DisplayName("저장하면 생성·수정 시각이 채워진다")
	void 생성_시각이_자동으로_채워진다() {
		LocalDateTime before = LocalDateTime.now();

		Customer customer = saved();
		entityManager.flush();

		assertThat(customer.getCreatedAt()).isNotNull().isAfterOrEqualTo(before);
		assertThat(customer.getUpdatedAt()).isNotNull();
	}

	@Test
	@DisplayName("수정하면 updatedAt만 바뀌고 createdAt은 그대로다")
	void 수정해도_생성_시각은_불변() throws Exception {
		Customer customer = saved();
		entityManager.flush();
		LocalDateTime created = customer.getCreatedAt();
		LocalDateTime firstUpdate = customer.getUpdatedAt();

		Thread.sleep(10);   // 시각 차이를 만들기 위한 최소 대기
		customer.usePoint(new BigDecimal("1000.00"));
		entityManager.flush();

		// updatable = false 라 UPDATE 문에 아예 포함되지 않는다 — 덮어쓸 방법이 없다
		assertThat(customer.getCreatedAt()).isEqualTo(created);
		assertThat(customer.getUpdatedAt()).isAfter(firstUpdate);
	}

	@Test
	@DisplayName("감사 필드에도 setter가 없다 — 리스너가 리플렉션으로 채운다")
	void 감사_필드에_setter가_없다() {
		// ArchUnit의 '엔티티에 public setter 없음' 규칙이 이 클래스에도 적용된다.
		// 여기서 다시 확인하는 이유는 상속으로 들어온 필드라 놓치기 쉬워서다
		assertThat(BaseTimeEntity.class.getMethods())
				.noneMatch(m -> m.getName().startsWith("set"));
	}
}
