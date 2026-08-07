package com.sk.skala.shopapi.customer.entity;

import java.math.BigDecimal;

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

	public static Customer register(String customerId, String customerPassword, BigDecimal initialPoint) {
		if (StringUtil.isAnyEmpty(customerId, customerPassword)) {
			throw new ParameterException("customerId, customerPassword");
		}
		Customer customer = new Customer();
		customer.customerId = customerId;
		customer.customerPassword = customerPassword;
		customer.customerPoint = initialPoint;
		return customer;
	}

	/** 비밀번호 비교를 밖으로 내보내지 않는다. Phase 2에서 BCrypt로 바꿔도 호출부는 그대로다. */
	public boolean matchesPassword(String rawPassword) {
		return customerPassword.equals(rawPassword);
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

	public void changePassword(String customerPassword) {
		if (StringUtil.isAnyEmpty(customerPassword)) {
			throw new ParameterException("customerPassword");
		}
		this.customerPassword = customerPassword;
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
