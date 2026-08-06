package com.sk.skala.shopapi.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sk.skala.shopapi.data.table.Customer;
import com.sk.skala.shopapi.data.dto.OrderListDto;
import com.sk.skala.shopapi.data.table.OrderItem;
import com.sk.skala.shopapi.data.dto.OrderRequest;
import com.sk.skala.shopapi.data.dto.CustomerSession;
import com.sk.skala.shopapi.data.dto.OrderItemDto;
import com.sk.skala.shopapi.data.table.Product;
import com.sk.skala.shopapi.exception.ParameterException;
import com.sk.skala.shopapi.exception.ResponseException;
import com.sk.skala.shopapi.repository.CustomerRepository;
import com.sk.skala.shopapi.repository.OrderItemRepository;
import com.sk.skala.shopapi.repository.ProductRepository;
import com.sk.skala.shopapi.exception.Error;
import com.sk.skala.shopapi.common.PagedList;
import com.sk.skala.shopapi.common.SessionHandler;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomerService {

	/** 가입 시 부여하는 초기 포인트 (SPEC.md 5절). */
	private static final double INITIAL_POINT = 1_000_000;

	private final CustomerRepository customerRepository;
	// 다른 도메인의 Repository를 직접 참조한다 — Phase 1에서 Service 경유로 바꾼다
	private final ProductRepository productRepository;
	private final OrderItemRepository orderItemRepository;
	// 웹 계층(쿠키·JWT)이 Service까지 들어와 있다 — Phase 1에서 제거 (DECISIONS.md 5절)
	private final SessionHandler sessionHandler;

	public PagedList<Customer> getCustomers(int offset, int count) {
		Page<Customer> page = customerRepository.findAll(PageRequest.of(offset, count));
		return PagedList.of(page.getTotalElements(), offset, count, page.getContent());
	}

	@Transactional(readOnly = true)
	public OrderListDto getCustomerOrders(String customerId) {
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

	public Customer createCustomer(Customer customer) {
		if (customer.getCustomerId() == null || customer.getCustomerPassword() == null) {
			throw new ParameterException("customerId, customerPassword");
		}
		if (customerRepository.existsById(customer.getCustomerId())) {
			throw new ResponseException(Error.DATA_DUPLICATED);
		}
		// 클라이언트가 customerPoint를 실어 보내면 그대로 반영된다 (Mass Assignment) — Phase 2에서 차단
		if (customer.getCustomerPoint() == null) {
			customer.setCustomerPoint(INITIAL_POINT);
		}
		return customerRepository.save(customer);
	}

	/**
	 * 로그인 검증 후 응답용 고객 정보를 돌려준다 (비밀번호 제외).
	 * 토큰 발급·쿠키 굽기는 Controller가 맡는다.
	 */
	public Customer login(CustomerSession session) {
		if (session.getCustomerId() == null || session.getCustomerPassword() == null) {
			throw new ParameterException("customerId, customerPassword");
		}
		Customer customer = findCustomer(session.getCustomerId());
		// 평문 비교 — BCrypt 해싱은 Phase 2
		if (!customer.getCustomerPassword().equals(session.getCustomerPassword())) {
			throw new ResponseException(Error.NOT_AUTHENTICATED);
		}

		// 조회한 엔티티의 비밀번호를 지워서 반환하면, 영속성 컨텍스트가 열려 있을 때
		// 더티 체킹으로 DB의 비밀번호까지 날아간다. 응답 전용 복사본을 만든다
		Customer result = new Customer();
		result.setCustomerId(customer.getCustomerId());
		result.setCustomerPoint(customer.getCustomerPoint());
		return result;
	}

	// 본인 확인이 없다 — 남의 계정도 고칠 수 있다 (BOLA). Phase 2에서 방어
	public Customer updateCustomer(Customer request) {
		Customer customer = findCustomer(request.getCustomerId());
		if (request.getCustomerPassword() != null) {
			customer.setCustomerPassword(request.getCustomerPassword());
		}
		if (request.getCustomerPoint() != null) {
			customer.setCustomerPoint(request.getCustomerPoint());
		}
		return customerRepository.save(customer);
	}

	public void deleteCustomer(Customer request) {
		// 주문 내역이 남아 있으면 FK 제약에 걸린다 — 참조 무결성 정책은 Phase 2에서 정한다
		customerRepository.delete(findCustomer(request.getCustomerId()));
	}

	@Transactional
	public void order(OrderRequest request) {
		String customerId = sessionHandler.getCustomerId();
		validate(request);

		Customer customer = findCustomer(customerId);
		Product product = findProduct(request.getProductId());

		// 수량이 음수여도 막지 않는다 — 포인트가 늘어난다. Phase 2에서 @Positive로 차단
		double total = product.getProductPrice() * request.getQuantity();
		if (customer.getCustomerPoint() < total) {
			throw new ResponseException(Error.INSUFFICIENT_FUNDS);
		}
		customer.setCustomerPoint(customer.getCustomerPoint() - total);

		OrderItem item = orderItemRepository.findByCustomerAndProduct(customer, product)
				.orElse(null);
		if (item == null) {
			item = new OrderItem();
			item.setCustomer(customer);
			item.setProduct(product);
			item.setQuantity(request.getQuantity());
		} else {
			// 재주문은 신규 행이 아니라 수량 누적 — 고객당 상품 1행 불변식
			item.setQuantity(item.getQuantity() + request.getQuantity());
		}
		orderItemRepository.save(item);
	}

	@Transactional
	public void cancel(OrderRequest request) {
		String customerId = sessionHandler.getCustomerId();
		validate(request);

		Customer customer = findCustomer(customerId);
		Product product = findProduct(request.getProductId());

		OrderItem item = orderItemRepository.findByCustomerAndProduct(customer, product)
				.orElseThrow(() -> new ResponseException(Error.DATA_NOT_FOUND));
		if (item.getQuantity() < request.getQuantity()) {
			throw new ResponseException(Error.INSUFFICIENT_QUANTITY);
		}

		// 주문 당시가 아니라 '현재' 가격으로 환불한다 — 가격이 바뀌면 원금을 넘길 수 있다.
		// Phase 1에서 orderedPrice 스냅샷으로 교정 (DECISIONS.md 2절)
		double refund = product.getProductPrice() * request.getQuantity();
		customer.setCustomerPoint(customer.getCustomerPoint() + refund);

		int remain = item.getQuantity() - request.getQuantity();
		if (remain == 0) {
			orderItemRepository.delete(item);
		} else {
			item.setQuantity(remain);
			orderItemRepository.save(item);
		}
	}

	private void validate(OrderRequest request) {
		if (request.getProductId() == null || request.getQuantity() == null) {
			throw new ParameterException("productId, quantity");
		}
	}

	private Customer findCustomer(String customerId) {
		if (customerId == null) {
			throw new ParameterException("customerId");
		}
		return customerRepository.findById(customerId)
				.orElseThrow(() -> new ResponseException(Error.DATA_NOT_FOUND));
	}

	private Product findProduct(Long productId) {
		return productRepository.findById(productId)
				.orElseThrow(() -> new ResponseException(Error.DATA_NOT_FOUND));
	}
}
