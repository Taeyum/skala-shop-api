package com.sk.skala.shopapi.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sk.skala.shopapi.data.table.Customer;
import com.sk.skala.shopapi.data.table.OrderItem;
import com.sk.skala.shopapi.data.table.Product;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

	List<OrderItem> findByCustomer(Customer customer);

	Optional<OrderItem> findByCustomerAndProduct(Customer customer, Product product);
}
