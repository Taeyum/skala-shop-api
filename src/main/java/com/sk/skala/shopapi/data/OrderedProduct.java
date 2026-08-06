package com.sk.skala.shopapi.data;

import lombok.Getter;
import lombok.Setter;

/** CustomerOrder의 products 배열 원소. */
@Getter
@Setter
public class OrderedProduct {

	private Long productId;
	private String productName;
	private Double productPrice;
	private Integer quantity;
}
