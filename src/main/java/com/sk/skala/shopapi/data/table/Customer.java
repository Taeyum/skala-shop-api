package com.sk.skala.shopapi.data.table;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "customers")
@Getter
@Setter
public class Customer {

	// 대리키 — 비즈니스 의미가 없어 도메인 규칙이 바뀌어도 영향받지 않는다 (DECISIONS.md 1절)
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 자연키 — PK는 아니지만 유일성은 DB가 보장한다. API 경로는 계속 이 값을 쓴다
	@Column(nullable = false, unique = true)
	private String customerId;

	@Column(nullable = false)
	private String customerPassword;

	@Column(nullable = false)
	private Double customerPoint;
}
