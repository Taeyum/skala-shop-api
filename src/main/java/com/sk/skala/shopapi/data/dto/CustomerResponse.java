package com.sk.skala.shopapi.data.dto;

import java.math.BigDecimal;

import com.sk.skala.shopapi.data.table.Customer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 고객 응답. 비밀번호는 애초에 필드가 없어 노출될 수 없다.
 * <p>
 * 대리키 `id`도 담지 않는다 — 내부 식별자일 뿐이고 API가 고객을 식별하는 값은 `customerId`다.
 * 이 지점에서 "대리키 전환의 API 영향 없음"(DECISIONS.md 1절)이 실제로 실현된다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {

	private String customerId;
	private BigDecimal customerPoint;

	public static CustomerResponse from(Customer customer) {
		return CustomerResponse.builder()
				.customerId(customer.getCustomerId())
				.customerPoint(customer.getCustomerPoint())
				.build();
	}
}
