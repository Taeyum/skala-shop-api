package com.sk.skala.shopapi.customer.entity;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;

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

	/**
	 * 응답 DTO에 비밀번호 필드가 없으므로 정상 경로로는 노출되지 않는다.
	 * {@code @JsonIgnore}는 <b>엔티티가 실수로 직렬화되는 경로</b>를 막는 두 번째 방어다 —
	 * 누군가 Controller에서 엔티티를 그대로 반환하거나, 디버깅용으로 로그에 JSON을 찍을 때.
	 * 한 겹이 뚫려도 다른 겹이 남아야 방어다.
	 */
	@JsonIgnore
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
		// 음수 결제액은 차감이 아니라 증가가 된다. Bean Validation은 웹 진입만 지키므로
		// 불변식은 여기서도 지킨다 (DECISIONS.md 9-4절)
		requirePositive(amount);
		if (customerPoint.compareTo(amount) < 0) {
			throw new ResponseException(Error.INSUFFICIENT_FUNDS);
		}
		this.customerPoint = this.customerPoint.subtract(amount);
	}

	/**
	 * 환불. <b>음수 환불을 막는 것이 핵심이다.</b>
	 * <p>
	 * 이 메서드에는 잔액 검사가 없다 — 환불은 잔액을 늘리는 연산이라 필요 없기 때문이다.
	 * 그래서 음수를 허용하면 {@code usePoint}의 잔액 검사를 <b>우회해 포인트를 차감</b>할 수 있고,
	 * 잔액이 음수가 된다. 실제로 취소 수량에 음수를 넣어 이 경로가 열려 있었다.
	 */
	public void refundPoint(BigDecimal amount) {
		requirePositive(amount);
		this.customerPoint = this.customerPoint.add(amount);
	}

	private static void requirePositive(BigDecimal amount) {
		if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new ParameterException("amount");
		}
	}

	/** {@code encodedPassword}는 이미 해싱된 값이어야 한다. */
	public void changePassword(String encodedPassword) {
		if (StringUtil.isAnyEmpty(encodedPassword)) {
			throw new ParameterException("customerPassword");
		}
		this.customerPassword = encodedPassword;
	}

	public void changePoint(BigDecimal customerPoint) {
		// 포인트는 음수가 될 수 없다. Phase 0~1에서는 강의 자료를 따라 DATA_NOT_FOUND로 던졌으나,
		// 검증 실패에 "데이터를 찾을 수 없음"은 의미가 맞지 않고 매핑하면 404가 된다.
		// 매핑 작업(Phase 2 B-1)이 이 부정확함을 드러내 ParameterException(400)으로 교정했다
		if (customerPoint.compareTo(BigDecimal.ZERO) < 0) {
			throw new ParameterException("customerPoint");
		}
		this.customerPoint = customerPoint;
	}
}
