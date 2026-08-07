package com.sk.skala.shopapi.data.table;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "products")
@Getter
@Setter
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 중복 상품명은 DATA_DUPLICATED — data.sql의 ON CONFLICT DO NOTHING도 이 제약에 의존한다
	@Column(nullable = false, unique = true)
	private String productName;

	// 금액은 BigDecimal. double은 십진 소수를 정확히 표현하지 못한다 (DECISIONS.md 3절)
	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal productPrice;
}
