package com.sk.skala.shopapi.data.table;

import java.math.BigDecimal;

import com.sk.skala.shopapi.exception.ParameterException;
import com.sk.skala.shopapi.tools.StringUtil;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 전용, 외부 생성 차단
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

	/** 생성 시점부터 유효한 상태만 허용한다. 검증을 통과하지 못하면 객체가 만들어지지 않는다. */
	public static Product of(String productName, BigDecimal productPrice) {
		Product product = new Product();
		product.assign(productName, productPrice);
		return product;
	}

	public void changeInfo(String productName, BigDecimal productPrice) {
		assign(productName, productPrice);
	}

	/** 상품명은 비어있을 수 없고 가격은 0 이하일 수 없다 — 이 불변식은 Product가 지킨다. */
	private void assign(String productName, BigDecimal productPrice) {
		// BigDecimal 비교는 compareTo — equals는 scale까지 보므로 0 != 0.00이 된다
		if (StringUtil.isAnyEmpty(productName)
				|| productPrice == null
				|| productPrice.compareTo(BigDecimal.ZERO) <= 0) {
			throw new ParameterException("productName, productPrice");
		}
		this.productName = productName;
		this.productPrice = productPrice;
	}
}
