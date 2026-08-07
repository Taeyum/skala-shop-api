package com.sk.skala.shopapi.order.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sk.skala.shopapi.customer.entity.Customer;
import com.sk.skala.shopapi.order.entity.OrderItem;
import com.sk.skala.shopapi.product.entity.Product;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

	/**
	 * 고객의 보유 상품 목록. <b>product를 함께 가져온다.</b>
	 * <p>
	 * 이 어노테이션이 없으면 {@code item.getProduct().getProductName()}이 호출될 때마다
	 * 상품이 개별 조회된다 — 상품 20종에 쿼리 22개(1+1+20)가 나갔다.
	 * 붙인 뒤 <b>2개</b>가 된다 (측정: {@code docs/evidence/n-plus-1.md}).
	 * <p>
	 * {@code Product}의 연관을 EAGER로 바꾸는 방법도 있으나 쓰지 않는다 —
	 * 상품이 필요 없는 조회에서도 조인이 붙어 전역적으로 더 나빠진다.
	 * <b>페치 전략은 매핑이 아니라 쿼리마다 정한다.</b>
	 * <p>
	 * 회귀는 {@code OrderQueryCountTest}가 막는다. 이 어노테이션을 지우면 그 테스트가 깨진다.
	 */
	@EntityGraph(attributePaths = {"product"})
	List<OrderItem> findByCustomer(Customer customer);

	Optional<OrderItem> findByCustomerAndProduct(Customer customer, Product product);
}
