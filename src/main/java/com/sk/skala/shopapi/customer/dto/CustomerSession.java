package com.sk.skala.shopapi.customer.dto;

import lombok.Getter;
import lombok.Setter;

/** 로그인 요청 바디 (POST /api/customers/login). */
@Getter
@Setter
public class CustomerSession {

	private String customerId;
	private String customerPassword;
}
