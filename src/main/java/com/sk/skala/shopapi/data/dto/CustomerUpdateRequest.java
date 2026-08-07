package com.sk.skala.shopapi.data.dto;

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

	private String customerId;
	private String customerPassword;
	private BigDecimal customerPoint;
}
