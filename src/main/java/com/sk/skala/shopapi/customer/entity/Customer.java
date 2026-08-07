package com.sk.skala.shopapi.customer.entity;

import java.math.BigDecimal;

import org.springframework.security.crypto.password.PasswordEncoder;

import com.sk.skala.shopapi.global.exception.Error;
import com.sk.skala.shopapi.global.exception.ParameterException;
import com.sk.skala.shopapi.global.exception.ResponseException;
import com.sk.skala.shopapi.global.tools.StringUtil;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "customers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 전용, 외부 생성 차단
public class Customer {

	// 대리키 — 비즈니스 의미가 없어 도메인 규칙이 바뀌어도 영향받지 않는다 (DECISIONS.md 1절)
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 자연키 — PK는 아니지만 유일성은 DB가 보장한다. API 경로는 계속 이 값을 쓴다
	@Column(nullable = false, unique = true)
	private String customerId;

	@Column(nullable = false)
	private String customerPassword;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal customerPoint;

	/** {@code encodedPassword}는 이미 해싱된 값이어야 한다. 해싱은 호출 전에 끝낸다. */
	public static Customer register(String customerId, String encodedPassword, BigDecimal initialPoint) {
		if (StringUtil.isAnyEmpty(customerId, encodedPassword)) {
			throw new ParameterException("customerId, customerPassword");
		}
		Customer customer = new Customer();
		customer.customerId = customerId;
		customer.customerPassword = encodedPassword;
		customer.customerPoint = initialPoint;
		return customer;
	}

	/**
	 * 비밀번호 일치 판단은 Customer가 한다. 다만 <b>어떤 알고리즘으로</b> 비교하는지는
	 * 엔티티가 알 일이 아니라 인코더에 위임한다 — 엔티티가 스프링 빈을 직접 물면 안 되므로
	 * 인자로 받는다. 알고리즘이 바뀌어도 이 메서드의 의미는 그대로다.
	 */
	public boolean matchesPassword(String rawPassword, PasswordEncoder passwordEncoder) {
		return passwordEncoder.matches(rawPassword, this.customerPassword);
	}

	/**
	 * 잔액 부족 여부를 스스로 판단한다 — 호출자가 검증을 잊어도 포인트는 음수가 되지 않는다.
	 * Service를 우회해 엔티티를 직접 다뤄도 이 불변식은 깨지지 않는다.
	 */
	public void usePoint(BigDecimal amount) {
		if (customerPoint.compareTo(amount) < 0) {
			throw new ResponseException(Error.INSUFFICIENT_FUNDS);
		}
		this.customerPoint = this.customerPoint.subtract(amount);
	}

	public void refundPoint(BigDecimal amount) {
		this.customerPoint = this.customerPoint.add(amount);
	}

	/** {@code encodedPassword}는 이미 해싱된 값이어야 한다. */
	public void changePassword(String encodedPassword) {
		if (StringUtil.isAnyEmpty(encodedPassword)) {
			throw new ParameterException("customerPassword");
		}
		this.customerPassword = encodedPassword;
	}

	public void changePoint(BigDecimal customerPoint) {
		// 포인트는 음수가 될 수 없다. 오류 코드가 DATA_NOT_FOUND인 것은 강의 자료를 따른 것으로,
		// 의미가 맞지 않는다는 판단은 Phase 2 "Error → HTTP 매핑"에서 교정한다 (DECISIONS.md 10절)
		if (customerPoint.compareTo(BigDecimal.ZERO) < 0) {
			throw new ResponseException(Error.DATA_NOT_FOUND, "invalid customerPoint");
		}
		this.customerPoint = customerPoint;
	}
}
