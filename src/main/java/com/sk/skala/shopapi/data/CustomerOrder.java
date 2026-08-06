package com.sk.skala.shopapi.data;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * GET /api/customers/{customerId} 응답 형태.
 * 엔티티만으로는 SPEC.md 2절이 요구하는 JSON 모양(products 배열에 상품 정보가 평탄화됨)을
 * 만들 수 없어 이 엔드포인트에만 별도 클래스를 둔다.
 */
@Getter
@Setter
public class CustomerOrder {

	private String customerId;
	private Double customerPoint;
	private List<OrderedProduct> products;
}
