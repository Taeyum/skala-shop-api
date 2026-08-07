package com.sk.skala.shopapi.customer.dto;

import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

/** 로그인 요청 바디 (POST /api/customers/login). */
@Getter
@Setter
public class CustomerSession {

	@NotBlank
	private String customerId;
	@NotBlank
	private String customerPassword;
}
