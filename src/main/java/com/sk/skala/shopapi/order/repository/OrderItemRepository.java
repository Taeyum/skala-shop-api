package com.sk.skala.shopapi.order.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sk.skala.shopapi.customer.entity.Customer;
import com.sk.skala.shopapi.order.entity.OrderItem;
import com.sk.skala.shopapi.product.entity.Product;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

	List<OrderItem> findByCustomer(Customer customer);

	Optional<OrderItem> findByCustomerAndProduct(Customer customer, Product product);
}
