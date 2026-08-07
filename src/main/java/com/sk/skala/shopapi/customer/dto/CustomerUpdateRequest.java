package com.sk.skala.shopapi.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 고객 정보 수정 요청. 강의 자료(인쇄 551)의 "고객 정보(주로 포인트) 업데이트"에 해당한다.
 * <p>
 * 가입용 {@link CustomerRequest}와 분리한 이유 — 수정은 포인트 변경이 정당한 기능이지만
 * 가입은 아니다. 하나의 DTO로 묶으면 가입에도 포인트 필드가 열려 Mass Assignment가 되살아난다.
 */
@Getter
@Setter
public class CustomerUpdateRequest {

	@NotBlank
	private String customerId;
	/**
	 * <b>받기만 하고 내보내지 않는다.</b> {@code WRITE_ONLY}면 Jackson이 역직렬화는 하되
	 * 직렬화에서는 이 필드를 아예 빼므로, 이 DTO가 로그·응답 어디로 나가든 값이 실리지 않는다.
	 * API 로깅이 요청 바디를 찍기 시작하면서 필요해졌다 (DECISIONS.md 20절).
	 * {@code SensitiveDataMasker}가 두 번째 겹이다 — 한 겹이 뚫려도 다른 겹이 남아야 방어다.
	 */
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String customerPassword;
	// 음수 거부. 엔티티(Customer.changePoint)에도 같은 불변식이 있다 — 웹 진입은 한 겹일 뿐이다
	@PositiveOrZero
	private BigDecimal customerPoint;
}
