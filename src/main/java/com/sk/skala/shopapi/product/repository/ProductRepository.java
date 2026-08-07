package com.sk.skala.shopapi.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sk.skala.shopapi.product.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

	boolean existsByProductName(String productName);
}
