package com.sk.skala.shopapi.customer.dto;

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

	private String customerId;
	private String customerPassword;
}
