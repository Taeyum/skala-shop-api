package com.sk.skala.shopapi.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/** POST /api/customers/order · /cancel 요청 바디. customerId는 바디로 받지 않고 JWT에서 꺼낸다. */
@Getter
@Setter
public class OrderRequest {

	@NotNull
	private Long productId;
	// 음수·0을 막는다. Phase 1까지는 quantity=-5 주문이 포인트를 늘렸다 (DECISIONS.md 9-4절)
	@NotNull
	@Positive
	private Integer quantity;
}
