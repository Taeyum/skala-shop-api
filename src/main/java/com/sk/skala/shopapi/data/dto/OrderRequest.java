package com.sk.skala.shopapi.data.dto;

import lombok.Getter;
import lombok.Setter;

/** POST /api/customers/order · /cancel 요청 바디. customerId는 바디로 받지 않고 JWT에서 꺼낸다. */
@Getter
@Setter
public class OrderRequest {

	private Long productId;
	private Integer quantity;
}
