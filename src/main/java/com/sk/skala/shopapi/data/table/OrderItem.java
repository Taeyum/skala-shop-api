package com.sk.skala.shopapi.data.table;

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
	 * 주문 시점 단가 스냅샷. 취소 환불은 현재가가 아니라 이 값을 쓴다.
	 * 가격은 이력성 데이터라 현재 값을 참조하면 과거 거래가 왜곡된다 (DECISIONS.md 2절).
	 */
	@Column(nullable = false)
	private Double orderedPrice;
}
