package com.sk.skala.shopapi.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sk.skala.shopapi.data.table.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

	// PK가 대리키로 바뀌어 자연키 조회는 별도 메서드가 필요하다. UNIQUE 인덱스를 탄다
	Optional<Customer> findByCustomerId(String customerId);

	boolean existsByCustomerId(String customerId);
}
