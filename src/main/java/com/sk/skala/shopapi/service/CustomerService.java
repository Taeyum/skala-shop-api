package com.sk.skala.shopapi.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sk.skala.shopapi.common.PagedList;
import com.sk.skala.shopapi.data.dto.CustomerRequest;
import com.sk.skala.shopapi.data.dto.CustomerResponse;
import com.sk.skala.shopapi.data.dto.CustomerSession;
import com.sk.skala.shopapi.data.dto.CustomerUpdateRequest;
import com.sk.skala.shopapi.data.dto.OrderItemDto;
import com.sk.skala.shopapi.data.dto.OrderListDto;
import com.sk.skala.shopapi.data.dto.OrderRequest;
import com.sk.skala.shopapi.data.table.Customer;
import com.sk.skala.shopapi.data.table.OrderItem;
import com.sk.skala.shopapi.data.table.Product;
import com.sk.skala.shopapi.exception.Error;
import com.sk.skala.shopapi.exception.ParameterException;
import com.sk.skala.shopapi.exception.ResponseException;
import com.sk.skala.shopapi.repository.CustomerRepository;
import com.sk.skala.shopapi.repository.OrderItemRepository;
import com.sk.skala.shopapi.repository.ProductRepository;
import com.sk.skala.shopapi.tools.StringUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomerService {

	/** 가입 시 부여하는 초기 포인트 (SPEC.md 5절). 문자열 생성자 — new BigDecimal(double)은 오차가 들어온다 */
	private static final BigDecimal INITIAL_POINT = new BigDecimal("1000000.00");

	private final CustomerRepository customerRepository;
	// 다른 도메인의 Repository를 직접 참조한다 — Phase 1에서 Service 경유로 바꾼다
	private final ProductRepository productRepository;
	private final OrderItemRepository orderItemRepository;

	public PagedList<CustomerResponse> getAllCustomers(int offset, int count) {
		Page<Customer> page = customerRepository.findAll(PageRequest.of(offset, count));
		// 엔티티를 그대로 내보내던 때는 비밀번호가 응답에 실렸다. DTO에는 그 필드가 아예 없다
		return PagedList.of(page.getTotalElements(), offset, count,
				page.getContent().stream().map(CustomerResponse::from).toList());
	}

	@Transactional(readOnly = true)
	public OrderListDto getCustomerById(String customerId) {
		Customer customer = findCustomer(customerId);

		List<OrderItemDto> products = new ArrayList<>();
		for (OrderItem item : orderItemRepository.findByCustomer(customer)) {
			// item.getProduct()마다 SELECT가 나간다 (N+1) — Phase 3에서 fetch join으로 개선
			Product product = item.getProduct();
			products.add(OrderItemDto.builder()
					.productId(product.getId())
					.productName(product.getProductName())
					.productPrice(product.getProductPrice())
					.quantity(item.getQuantity())
					.build());
		}

		return OrderListDto.builder()
				.customerId(customer.getCustomerId())
				.customerPoint(customer.getCustomerPoint())
				.products(products)
				.build();
	}

	public CustomerResponse createCustomer(CustomerRequest request) {
		// 입력 검증은 register가 한다. 초기 포인트는 서버가 정하며, CustomerRequest에
		// customerPoint 필드가 없으므로 클라이언트는 지정할 방법 자체가 없다 (Mass Assignment 구조적 차단)
		Customer customer = Customer.register(
				request.getCustomerId(), request.getCustomerPassword(), INITIAL_POINT);
		if (customerRepository.existsByCustomerId(customer.getCustomerId())) {
			throw new ResponseException(Error.DATA_DUPLICATED, "Customer already exists");
		}
		return CustomerResponse.from(customerRepository.save(customer));
	}

	public CustomerResponse loginCustomer(CustomerSession customerSession) {
		if (StringUtil.isAnyEmpty(customerSession.getCustomerId(),
				customerSession.getCustomerPassword())) {
			throw new ParameterException("customerId, customerPassword");
		}
		Customer customer = findCustomer(customerSession.getCustomerId());
		// 비교 방식은 Customer가 안다 — Phase 2에서 BCrypt로 바꿔도 이 호출부는 그대로다
		if (!customer.matchesPassword(customerSession.getCustomerPassword())) {
			throw new ResponseException(Error.NOT_AUTHENTICATED, "password mismatch");
		}
		// 토큰 발급·쿠키 적재는 웹 관심사라 Controller가 맡는다.
		// 엔티티를 반환하던 때는 비밀번호를 빼려고 null을 세팅했고, 영속 상태였다면
		// 더티 체킹으로 DB의 비밀번호까지 지워질 코드였다 (JOURNAL.md 2026-08-07).
		// 응답 DTO에는 비밀번호 필드가 없으므로 그 조작 자체가 불필요해졌다
		return CustomerResponse.from(customer);
	}

	// 본인 확인이 없다 — 남의 계정도 고칠 수 있다 (BOLA). Phase 2에서 방어
	public CustomerResponse updateCustomer(CustomerUpdateRequest request) {
		Customer customer = findCustomer(request.getCustomerId());
		// 각 값의 유효성은 Customer가 판단한다 (음수 포인트 거부 등)
		if (request.getCustomerPassword() != null) {
			customer.changePassword(request.getCustomerPassword());
		}
		if (request.getCustomerPoint() != null) {
			customer.changePoint(request.getCustomerPoint());
		}
		return CustomerResponse.from(customerRepository.save(customer));
	}

	public void deleteCustomer(CustomerRequest request) {
		// 주문 내역이 남아 있으면 FK 제약에 걸린다 — 참조 무결성 정책은 Phase 2에서 정한다
		customerRepository.delete(findCustomer(request.getCustomerId()));
	}

	@Transactional
	public void placeOrder(String customerId, OrderRequest order) {
		validate(order);

		Customer customer = findCustomer(customerId);
		Product product = findProduct(order.getProductId());

		// 수량이 음수여도 막지 않는다 — 포인트가 늘어난다. Phase 2에서 @Positive로 차단
		BigDecimal total = product.getProductPrice()
				.multiply(BigDecimal.valueOf(order.getQuantity()));
		// 잔액 부족 판단은 Customer가 한다 — 여기서 검증을 잊어도 포인트는 음수가 되지 않는다
		customer.usePoint(total);

		OrderItem item = orderItemRepository.findByCustomerAndProduct(customer, product)
				.orElse(null);
		if (item == null) {
			item = OrderItem.of(customer, product, order.getQuantity(), total);
		} else {
			// 재주문은 신규 행이 아니라 수량 누적 — (customer_id, product_id) 복합 UNIQUE가 강제한다
			item.addOrder(order.getQuantity(), total);
		}
		orderItemRepository.save(item);
	}

	@Transactional
	public void cancelOrder(String customerId, OrderRequest order) {
		validate(order);

		Customer customer = findCustomer(customerId);
		Product product = findProduct(order.getProductId());

		OrderItem item = orderItemRepository.findByCustomerAndProduct(customer, product)
				.orElseThrow(() -> new ResponseException(Error.DATA_NOT_FOUND, "OrderItem not found"));

		// 보유 수량 검증·환불액 계산·반올림 규칙이 모두 OrderItem 안에 있다.
		// 현재가가 아니라 결제 총액에서 환불하는 이유는 DECISIONS.md 2절
		BigDecimal refund = item.cancel(order.getQuantity());
		customer.refundPoint(refund);

		if (item.isEmpty()) {
			orderItemRepository.delete(item);
		} else {
			orderItemRepository.save(item);
		}
	}

	private void validate(OrderRequest order) {
		if (order.getProductId() == null || order.getQuantity() == null) {
			throw new ParameterException("productId, quantity");
		}
	}

	private Customer findCustomer(String customerId) {
		if (customerId == null) {
			throw new ParameterException("customerId");
		}
		// PK가 아니라 자연키로 찾는다 — API 경로는 여전히 customerId를 쓴다 (DECISIONS.md 1절)
		return customerRepository.findByCustomerId(customerId)
				.orElseThrow(() -> new ResponseException(Error.DATA_NOT_FOUND, "Customer not found"));
	}

	private Product findProduct(Long productId) {
		return productRepository.findById(productId)
				.orElseThrow(() -> new ResponseException(Error.DATA_NOT_FOUND, "Product not found"));
	}
}
