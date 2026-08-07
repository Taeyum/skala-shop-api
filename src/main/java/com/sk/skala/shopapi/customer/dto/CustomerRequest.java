package com.sk.skala.shopapi.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

/**
 * 회원가입·탈퇴 요청.
 * <p>
 * <b>`customerPoint` 필드가 없는 것이 핵심이다.</b> 엔티티를 그대로 받던 때는 클라이언트가
 * 가입 요청에 포인트를 실어 보내면 그대로 저장됐다 (Mass Assignment). 받을 수 있는 필드를
 * 요청 DTO에 정의하지 않으면 그 공격 표면 자체가 사라진다 — 검증으로 막는 것보다 확실하다.
 */
@Getter
@Setter
public class CustomerRequest {

	@NotBlank
	private String customerId;
	/**
	 * <b>받기만 하고 내보내지 않는다.</b> {@code WRITE_ONLY}면 Jackson이 역직렬화는 하되
	 * 직렬화에서는 이 필드를 아예 빼므로, 이 DTO가 로그·응답 어디로 나가든 값이 실리지 않는다.
	 * API 로깅이 요청 바디를 찍기 시작하면서 필요해졌다 (DECISIONS.md 20절).
	 * {@code SensitiveDataMasker}가 두 번째 겹이다 — 한 겹이 뚫려도 다른 겹이 남아야 방어다.
	 */
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	@NotBlank
	private String customerPassword;
}
