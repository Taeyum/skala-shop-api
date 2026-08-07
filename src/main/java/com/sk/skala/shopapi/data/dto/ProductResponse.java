package com.sk.skala.shopapi.data.dto;

import java.math.BigDecimal;

import com.sk.skala.shopapi.data.table.Product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 상품 응답.
 * <p>
 * 고객과 달리 `id`를 담는다 — 주문 요청이 `productId`로 상품을 지목하므로(SPEC.md 2절)
 * 클라이언트가 목록 조회에서 id를 알아야 한다. 즉 상품의 id는 내부 식별자가 아니라 계약의 일부다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

	private Long id;
	private String productName;
	private BigDecimal productPrice;

	public static ProductResponse from(Product product) {
		return ProductResponse.builder()
				.id(product.getId())
				.productName(product.getProductName())
				.productPrice(product.getProductPrice())
				.build();
	}
}
