package com.sk.skala.shopapi.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sk.skala.shopapi.common.PagedList;
import com.sk.skala.shopapi.common.Response;
import com.sk.skala.shopapi.common.SessionHandler;
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
	// 웹 계층(쿠키·JWT)이 Service까지 들어와 있다 — Phase 1에서 제거 (DECISIONS.md 5절)
	private final SessionHandler sessionHandler;

	public Response<PagedList<CustomerResponse>> getAllCustomers(int offset, int count) {
		Page<Customer> page = customerRepository.findAll(PageRequest.of(offset, count));
		// 엔티티를 그대로 내보내던 때는 비밀번호가 응답에 실렸다. DTO에는 그 필드가 아예 없다
		return Response.success(PagedList.of(page.getTotalElements(), offset, count,
				page.getContent().stream().map(CustomerResponse::from).toList()));
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

	public Response<CustomerResponse> createCustomer(CustomerRequest request) {
		if (StringUtil.isAnyEmpty(request.getCustomerId(), request.getCustomerPassword())) {
			throw new ParameterException("customerId, customerPassword");
		}
		if (customerRepository.existsByCustomerId(request.getCustomerId())) {
			throw new ResponseException(Error.DATA_DUPLICATED, "Customer already exists");
		}
		Customer customer = new Customer();
		customer.setCustomerId(request.getCustomerId());
		customer.setCustomerPassword(request.getCustomerPassword());
		// 초기 포인트는 서버가 정한다. CustomerRequest에 customerPoint 필드가 없으므로
		// 클라이언트는 이 값을 지정할 방법 자체가 없다 (Mass Assignment 구조적 차단)
		customer.setCustomerPoint(INITIAL_POINT);
		return Response.success(CustomerResponse.from(customerRepository.save(customer)));
	}

	public Response<CustomerResponse> loginCustomer(CustomerSession customerSession) {
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

		// 엔티티를 반환하던 때는 비밀번호를 빼려고 null을 세팅했고, 영속 상태였다면
		// 더티 체킹으로 DB의 비밀번호까지 지워질 코드였다 (JOURNAL.md 2026-08-07).
		// 응답 DTO에는 비밀번호 필드가 없으므로 그 조작 자체가 불필요해졌다
		return Response.success(CustomerResponse.from(customer));
	}

	// 본인 확인이 없다 — 남의 계정도 고칠 수 있다 (BOLA). Phase 2에서 방어
	public Response<CustomerResponse> updateCustomer(CustomerUpdateRequest request) {
		// 자료(인쇄 551)는 customerId 존재 확인과 customerPoint 유효성을 한 줄에 묶어
		// 둘 다 DATA_NOT_FOUND로 적었다. 검증 실패에 "데이터를 찾을 수 없음"은 의미가 맞지 않지만,
		// 그 부정확함 자체가 Phase 2 "Error → HTTP 매핑"의 개선 대상이므로 기준점에서는 자료를 따른다.
		// 지금 고치면 Phase 2가 매핑만 붙이는 작업이 되고 개선 전/후가 사라진다 (DECISIONS.md 10절)
		if (request.getCustomerPoint() != null
				&& request.getCustomerPoint().compareTo(BigDecimal.ZERO) < 0) {
			throw new ResponseException(Error.DATA_NOT_FOUND, "invalid customerPoint");
		}
		Customer customer = findCustomer(request.getCustomerId());
		if (request.getCustomerPassword() != null) {
			customer.setCustomerPassword(request.getCustomerPassword());
		}
		if (request.getCustomerPoint() != null) {
			customer.setCustomerPoint(request.getCustomerPoint());
		}
		return Response.success(CustomerResponse.from(customerRepository.save(customer)));
	}

	public Response<Void> deleteCustomer(CustomerRequest request) {
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
		BigDecimal total = product.getProductPrice()
				.multiply(BigDecimal.valueOf(order.getQuantity()));
		if (customer.getCustomerPoint().compareTo(total) < 0) {
			throw new ResponseException(Error.INSUFFICIENT_FUNDS);
		}
		customer.setCustomerPoint(customer.getCustomerPoint().subtract(total));

		OrderItem item = orderItemRepository.findByCustomerAndProduct(customer, product)
				.orElse(null);
		if (item == null) {
			item = new OrderItem();
			item.setCustomer(customer);
			item.setProduct(product);
			item.setQuantity(order.getQuantity());
			item.setOrderedAmount(total);
		} else {
			// 재주문은 신규 행이 아니라 수량 누적 — (customer_id, product_id) 복합 UNIQUE가 강제한다.
			// 총액을 저장하므로 결제액을 더하기만 하면 된다. 나눗셈이 없어 오차가 생길 여지도 없다
			item.setQuantity(item.getQuantity() + order.getQuantity());
			item.setOrderedAmount(item.getOrderedAmount().add(total));
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

		// 현재가가 아니라 결제 총액에서 환불한다. 현재가를 쓰면 가격 상승 후 취소할 때 원금을 초과한다
		// (실측: 15,000짜리 2개 주문 후 50,000으로 인상하고 1개 취소 → 잔액이 원금보다 20,000 많았다).
		// 근거는 DECISIONS.md 2절
		int remain = item.getQuantity() - order.getQuantity();
		BigDecimal refund;
		if (remain == 0) {
			// 전량 취소는 잔액 전부. 나눗셈이 없으니 반올림이 개입할 여지도 없다
			refund = item.getOrderedAmount();
		} else {
			// 곱한 뒤 나눈다 — 반올림을 1회로 줄인다.
			// DOWN이라 개별 환불이 정확한 몫을 넘지 않는다 (검증: DECISIONS.md 3절)
			refund = item.getOrderedAmount()
					.multiply(BigDecimal.valueOf(order.getQuantity()))
					.divide(BigDecimal.valueOf(item.getQuantity()), 2, RoundingMode.DOWN);
		}
		customer.setCustomerPoint(customer.getCustomerPoint().add(refund));

		if (remain == 0) {
			orderItemRepository.delete(item);
		} else {
			item.setQuantity(remain);
			// 남은 총액은 재계산이 아니라 차감이다. 재계산하면 반올림이 매번 새로 일어나 누적되지만,
			// 차감하면 잔여가 잔액에 남아 다음 취소로 이월돼 환불 합계가 결제 총액과 정확히 일치한다
			item.setOrderedAmount(item.getOrderedAmount().subtract(refund));
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
