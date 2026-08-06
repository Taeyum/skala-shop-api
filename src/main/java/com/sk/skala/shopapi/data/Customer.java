package com.sk.skala.shopapi.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "customers")
@Getter
@Setter
public class Customer {

	// 스펙대로 자연키를 PK로 쓴다 (Phase 1에서 대리키로 전환 — DECISIONS.md 1절)
	@Id
	private String customerId;

	@Column(nullable = false)
	private String customerPassword;

	@Column(nullable = false)
	private Double customerPoint;
}
