package com.sk.skala.shopapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sk.skala.shopapi.data.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

	boolean existsByProductName(String productName);
}
