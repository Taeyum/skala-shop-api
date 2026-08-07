package com.sk.skala.shopapi.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sk.skala.shopapi.common.PagedList;
import com.sk.skala.shopapi.common.Response;
import com.sk.skala.shopapi.common.SessionHandler;
import com.sk.skala.shopapi.data.dto.CustomerSession;
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

	/** 가입 시 부여하는 초기 포인트 (SPEC.md 5절). */
	private static final double INITIAL_POINT = 1_000_000;

	private final CustomerRepository customerRepository;
	// 다른 도메인의 Repository를 직접 참조한다 — Phase 1에서 Service 경유로 바꾼다
	private final ProductRepository productRepository;
	private final OrderItemRepository orderItemRepository;
	// 웹 계층(쿠키·JWT)이 Service까지 들어와 있다 — Phase 1에서 제거 (DECISIONS.md 5절)
	private final SessionHandler sessionHandler;

	public Response<PagedList<Customer>> getAllCustomers(int offset, int count) {
		Page<Customer> page = customerRepository.findAll(PageRequest.of(offset, count));
		return Response.success(
				PagedList.of(page.getTotalElements(), offset, count, page.getContent()));
	}

	@Transactional(readOnly = true)
	public Response<OrderListDto> getCustomerById(String customerId) {
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

		return Response.success(OrderListDto.builder()
				.customerId(customer.getCustomerId())
				.customerPoint(customer.getCustomerPoint())
				.products(products)
				.build());
	}

	public Response<Customer> createCustomer(Customer customer) {
		if (StringUtil.isAnyEmpty(customer.getCustomerId(), customer.getCustomerPassword())) {
			throw new ParameterException("customerId, customerPassword");
		}
		if (customerRepository.existsByCustomerId(customer.getCustomerId())) {
			throw new ResponseException(Error.DATA_DUPLICATED, "Customer already exists");
		}
		// 클라이언트가 customerPoint를 실어 보내면 그대로 반영된다 (Mass Assignment) — Phase 2에서 차단
		if (customer.getCustomerPoint() == null) {
			customer.setCustomerPoint(INITIAL_POINT);
		}
		return Response.success(customerRepository.save(customer));
	}

	public Response<Customer> loginCustomer(CustomerSession customerSession) {
		if (StringUtil.isAnyEmpty(customerSession.getCustomerId(),
				customerSession.getCustomerPassword())) {
			throw new ParameterException("customerId, customerPassword");
		}
		Customer customer = findCustomer(customerSession.getCustomerId());
		// 평문 비교 — BCrypt 해싱은 Phase 2
		if (!customer.getCustomerPassword().equals(customerSession.getCustomerPassword())) {
			throw new ResponseException(Error.NOT_AUTHENTICATED, "password mismatch");
		}
		sessionHandler.storeAccessToken(customer.getCustomerId());

		// 조회한 엔티티의 비밀번호를 지워서 반환하면, 영속성 컨텍스트가 열려 있을 때
		// 더티 체킹으로 DB의 비밀번호까지 날아간다. 응답 전용 복사본을 만든다
		Customer result = new Customer();
		result.setCustomerId(customer.getCustomerId());
		result.setCustomerPoint(customer.getCustomerPoint());
		return Response.success(result);
	}

	// 본인 확인이 없다 — 남의 계정도 고칠 수 있다 (BOLA). Phase 2에서 방어
	public Response<Customer> updateCustomer(Customer request) {
		// 자료(인쇄 551)는 customerId 존재 확인과 customerPoint 유효성을 한 줄에 묶어
		// 둘 다 DATA_NOT_FOUND로 적었다. 검증 실패에 "데이터를 찾을 수 없음"은 의미가 맞지 않지만,
		// 그 부정확함 자체가 Phase 2 "Error → HTTP 매핑"의 개선 대상이므로 기준점에서는 자료를 따른다.
		// 지금 고치면 Phase 2가 매핑만 붙이는 작업이 되고 개선 전/후가 사라진다 (DECISIONS.md 10절)
		if (request.getCustomerPoint() != null && request.getCustomerPoint() < 0) {
			throw new ResponseException(Error.DATA_NOT_FOUND, "invalid customerPoint");
		}
		Customer customer = findCustomer(request.getCustomerId());
		if (request.getCustomerPassword() != null) {
			customer.setCustomerPassword(request.getCustomerPassword());
		}
		if (request.getCustomerPoint() != null) {
			customer.setCustomerPoint(request.getCustomerPoint());
		}
		return Response.success(customerRepository.save(customer));
	}

	public Response<Void> deleteCustomer(Customer request) {
		// 주문 내역이 남아 있으면 FK 제약에 걸린다 — 참조 무결성 정책은 Phase 2에서 정한다
		customerRepository.delete(findCustomer(request.getCustomerId()));
		return Response.success();
	}

	@Transactional
	public Response<Void> placeOrder(OrderRequest order) {
		String customerId = sessionHandler.getCustomerId();
		validate(order);

		Customer customer = findCustomer(customerId);
		Product product = findProduct(order.getProductId());

		// 수량이 음수여도 막지 않는다 — 포인트가 늘어난다. Phase 2에서 @Positive로 차단
		double total = product.getProductPrice() * order.getQuantity();
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
			item.setQuantity(order.getQuantity());
		} else {
			// 재주문은 신규 행이 아니라 수량 누적 — 고객당 상품 1행 불변식
			item.setQuantity(item.getQuantity() + order.getQuantity());
		}
		orderItemRepository.save(item);
		return Response.success();
	}

	@Transactional
	public Response<Void> cancelOrder(OrderRequest order) {
		String customerId = sessionHandler.getCustomerId();
		validate(order);

		Customer customer = findCustomer(customerId);
		Product product = findProduct(order.getProductId());

		OrderItem item = orderItemRepository.findByCustomerAndProduct(customer, product)
				.orElseThrow(() -> new ResponseException(Error.DATA_NOT_FOUND, "OrderItem not found"));
		if (item.getQuantity() < order.getQuantity()) {
			throw new ResponseException(Error.INSUFFICIENT_QUANTITY);
		}

		// 주문 당시가 아니라 '현재' 가격으로 환불한다 — 가격이 바뀌면 원금을 넘길 수 있다.
		// Phase 1에서 orderedPrice 스냅샷으로 교정 (DECISIONS.md 2절)
		double refund = product.getProductPrice() * order.getQuantity();
		customer.setCustomerPoint(customer.getCustomerPoint() + refund);

		int remain = item.getQuantity() - order.getQuantity();
		if (remain == 0) {
			orderItemRepository.delete(item);
		} else {
			item.setQuantity(remain);
			orderItemRepository.save(item);
		}
		return Response.success();
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
