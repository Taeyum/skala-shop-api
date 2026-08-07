package com.sk.skala.shopapi.data.table;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "order_items",
		// 고객당 상품 1행 불변식을 DB가 강제한다 — 동시 재주문으로 행이 2개 생기는 것을 막는다
		uniqueConstraints = @UniqueConstraint(
				name = "uk_order_items_customer_product",
				columnNames = {"customer_id", "product_id"}))
@Getter
@Setter
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id", nullable = false)
	private Customer customer;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	private Integer quantity;

	/**
	 * 현재 보유 수량에 대해 실제로 결제한 <b>총액</b>. 단가가 아니다.
	 * <p>
	 * SPEC 3절이 "재주문 시 수량 누적(신규 행 생성 아님)"을 요구해 여러 주문이 한 행에 병합된다.
	 * 병합된 행에서는 단가로 결제 총액을 복원할 수 없다 — 40,000을 3개에 결제하면 단가가
	 * 13,333.33…이 되고, 저장하는 순간 40,000이라는 사실이 소실된다.
	 * 총액을 저장하면 전량 취소가 나눗셈 없이 정확해진다 (DECISIONS.md 2절).
	 */
	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal orderedAmount;
}
