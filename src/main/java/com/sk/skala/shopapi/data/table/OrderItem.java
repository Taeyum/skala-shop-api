package com.sk.skala.shopapi.data.table;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.sk.skala.shopapi.exception.Error;
import com.sk.skala.shopapi.exception.ResponseException;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_items",
		// 고객당 상품 1행 불변식을 DB가 강제한다 — 동시 재주문으로 행이 2개 생기는 것을 막는다
		uniqueConstraints = @UniqueConstraint(
				name = "uk_order_items_customer_product",
				columnNames = {"customer_id", "product_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 전용, 외부 생성 차단
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
	 * 병합된 행에서는 단가로 결제 총액을 복원할 수 없다 (DECISIONS.md 2절).
	 */
	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal orderedAmount;

	public static OrderItem of(Customer customer, Product product, int quantity, BigDecimal amount) {
		OrderItem item = new OrderItem();
		item.customer = customer;
		item.product = product;
		item.quantity = quantity;
		item.orderedAmount = amount;
		return item;
	}

	/**
	 * 재주문. <b>수량과 결제액을 함께</b> 더한다.
	 * <p>
	 * 이름이 {@code addQuantity}가 아닌 이유 — 이 연산은 수량만 바꾸지 않는다. `orderedAmount`가
	 * 같이 움직여야 환불 총액이 결제 총액과 일치한다. 수량만 다루는 것처럼 읽히는 이름을 두면
	 * 나중에 "수량만 조정하면 되는데" 하고 한쪽만 바꾸는 호출이 생긴다. 거래 한 건을 더한다는
	 * 뜻이 이름에 드러나야 한다.
	 */
	public void addOrder(int quantity, BigDecimal amount) {
		this.quantity += quantity;
		this.orderedAmount = this.orderedAmount.add(amount);
	}

	/**
	 * 취소. 환불액을 계산해 돌려주고 수량·결제액을 함께 줄인다.
	 * <p>
	 * 보유 수량을 초과해 취소할 수 없다는 불변식과 반올림 규칙이 모두 이 안에 있다.
	 * 환불액 계산이 {@code orderedAmount}와 {@code quantity} 둘 다에 의존하므로
	 * 이 계산을 밖에 두면 호출자가 내부 상태를 꺼내 써야 한다.
	 */
	public BigDecimal cancel(int quantity) {
		if (this.quantity < quantity) {
			throw new ResponseException(Error.INSUFFICIENT_QUANTITY);
		}
		int remain = this.quantity - quantity;
		BigDecimal refund;
		if (remain == 0) {
			// 전량 취소는 잔액 전부. 나눗셈이 없으니 반올림이 개입할 여지도 없다
			refund = this.orderedAmount;
		} else {
			// 곱한 뒤 나눈다(반올림 1회) + DOWN — 개별 환불이 정확한 몫을 넘지 않는다
			refund = this.orderedAmount
					.multiply(BigDecimal.valueOf(quantity))
					.divide(BigDecimal.valueOf(this.quantity), 2, RoundingMode.DOWN);
		}
		this.quantity = remain;
		// 남은 총액은 재계산이 아니라 차감 — 반올림 잔여가 잔액에 남아 다음 취소로 이월된다
		this.orderedAmount = this.orderedAmount.subtract(refund);
		return refund;
	}

	/** 전량 취소되어 행을 지워야 하는 상태인가. */
	public boolean isEmpty() {
		return this.quantity == 0;
	}
}
