package com.sk.skala.shopapi.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 고객이 보유한 상품 정보 — 상품 엔터티에 수량을 더한 형태. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDto {

	private Long productId;
	private String productName;
	private Double productPrice;
	private Integer quantity;
}
