package com.sk.skala.shopapi.order.dto;

import java.math.BigDecimal;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 고객이 주문한 상품 목록 조회 응답 (GET /api/customers/{customerId}). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderListDto {

	private String customerId;
	private BigDecimal customerPoint;
	private List<OrderItemDto> products;
}
