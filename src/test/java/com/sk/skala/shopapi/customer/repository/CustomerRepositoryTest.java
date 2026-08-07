package com.sk.skala.shopapi.customer.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.sk.skala.shopapi.customer.entity.Customer;
import com.sk.skala.shopapi.support.PostgresTestContainer;

import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PostgresTestContainer.class)
class CustomerRepositoryTest {

	@Autowired private CustomerRepository customerRepository;
	@Autowired private EntityManager entityManager;

	private Customer saved(String id) {
		return customerRepository.save(Customer.register(id, "pw", new BigDecimal("1000000.00")));
	}

	@Test
	void 자연키로_조회한다() {
		String id = "cust-" + System.nanoTime();
		saved(id);
		entityManager.flush();
		entityManager.clear();

		assertThat(customerRepository.findByCustomerId(id)).isPresent();
		assertThat(customerRepository.findByCustomerId("nobody")).isEmpty();
	}

	@Test
	void 대리키와_자연키가_분리돼_있다() {
		// PK는 Long id, customerId는 UNIQUE 제약일 뿐이다 (DECISIONS.md 1절)
		Customer customer = saved("cust-" + System.nanoTime());

		assertThat(customer.getId()).isNotNull();
		assertThat(customer.getCustomerId()).isNotEqualTo(String.valueOf(customer.getId()));
	}

	@Test
	void 중복_customerId는_DB가_거부한다() {
		// 애플리케이션의 existsByCustomerId 검사는 경합에서 뚫린다 — DB 제약이 최종 방어선이다
		String id = "dup-" + System.nanoTime();
		saved(id);
		entityManager.flush();

		// ★ flush가 아니라 save에서 터진다. @GeneratedValue(IDENTITY)는 PK를 DB가 만들어야 해서
		// INSERT를 트랜잭션 끝까지 미룰 수 없다 — persist 시점에 즉시 INSERT가 나간다.
		// SEQUENCE 전략이었다면 flush까지 지연됐을 것이다
		assertThatThrownBy(() -> saved(id))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void existsByCustomerId가_동작한다() {
		String id = "ex-" + System.nanoTime();
		saved(id);
		entityManager.flush();

		assertThat(customerRepository.existsByCustomerId(id)).isTrue();
		assertThat(customerRepository.existsByCustomerId("nobody")).isFalse();
	}

	@Test
	void 저장하면_version이_0으로_초기화된다() {
		// @Version이 붙어 있는지 — 없으면 null이 되고 낙관적 락이 동작하지 않는다
		Customer customer = saved("ver-" + System.nanoTime());
		entityManager.flush();

		assertThat(customer.getVersion()).isNotNull().isZero();
	}
}
