package com.sk.skala.shopapi.customer.service;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.sk.skala.shopapi.customer.dto.CustomerRequest;
import com.sk.skala.shopapi.customer.dto.CustomerResponse;
import com.sk.skala.shopapi.customer.dto.CustomerSession;
import com.sk.skala.shopapi.customer.dto.CustomerUpdateRequest;
import com.sk.skala.shopapi.customer.entity.Customer;
import com.sk.skala.shopapi.customer.repository.CustomerRepository;
import com.sk.skala.shopapi.global.common.PagedList;
import com.sk.skala.shopapi.global.exception.Error;
import com.sk.skala.shopapi.global.exception.ParameterException;
import com.sk.skala.shopapi.global.exception.ResponseException;
import com.sk.skala.shopapi.global.tools.StringUtil;

import lombok.RequiredArgsConstructor;

/**
 * 고객 도메인. 자기 Repository만 참조하며 다른 도메인을 알지 않는다.
 * 주문은 {@code order} 도메인이 이 Service를 경유해 고객을 가져간다 (단방향).
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

	/** 가입 시 부여하는 초기 포인트 (SPEC.md 5절). 문자열 생성자 — new BigDecimal(double)은 오차가 들어온다 */
	private static final BigDecimal INITIAL_POINT = new BigDecimal("1000000.00");

	private final CustomerRepository customerRepository;
	private final PasswordEncoder passwordEncoder;

	public PagedList<CustomerResponse> getAllCustomers(int offset, int count) {
		Page<Customer> page = customerRepository.findAll(PageRequest.of(offset, count));
		// 엔티티를 그대로 내보내던 때는 비밀번호가 응답에 실렸다. DTO에는 그 필드가 아예 없다
		return PagedList.of(page.getTotalElements(), offset, count,
				page.getContent().stream().map(CustomerResponse::from).toList());
	}

	public CustomerResponse createCustomer(CustomerRequest request) {
		// 입력 검증은 register가 한다. 초기 포인트는 서버가 정하며, CustomerRequest에
		// customerPoint 필드가 없으므로 클라이언트는 지정할 방법 자체가 없다 (Mass Assignment 구조적 차단)
		// 평문은 여기서 끝난다 — 엔티티에는 해시만 넘어간다
		String encodedPassword = request.getCustomerPassword() == null
				? null
				: passwordEncoder.encode(request.getCustomerPassword());
		Customer customer = Customer.register(
				request.getCustomerId(), encodedPassword, INITIAL_POINT);
		if (customerRepository.existsByCustomerId(customer.getCustomerId())) {
			throw new ResponseException(Error.DATA_DUPLICATED, "Customer already exists");
		}
		return CustomerResponse.from(customerRepository.save(customer));
	}

	public CustomerResponse loginCustomer(CustomerSession customerSession) {
		// 요청 바디에 값이 들어왔는지 보는 형태 검사다 (도메인 불변식이 아니다).
		// Phase 2 Bean Validation에서 Controller 계층으로 올라간다
		if (StringUtil.isAnyEmpty(customerSession.getCustomerId(),
				customerSession.getCustomerPassword())) {
			throw new ParameterException("customerId, customerPassword");
		}
		// findCustomer를 쓰지 않는다. 그러면 없는 ID는 DATA_NOT_FOUND(404), 틀린 비밀번호는
		// NOT_AUTHENTICATED(401)로 갈려 공격자가 응답만 보고 '유효한 ID 목록'을 만들 수 있다
		// — 사용자 열거(User Enumeration). 두 경우 모두 401로 통일한다 (DECISIONS.md 9-3절)
		Customer customer = customerRepository.findByCustomerId(customerSession.getCustomerId())
				.orElse(null);
		// 일치 판단은 Customer가 한다. BCrypt로 바꾸며 인코더 인자가 하나 늘었을 뿐,
		// '무엇이 일치인가'를 Service가 아는 구조로 돌아가지는 않았다.
		// 로그에는 사유를 구분해 남긴다 — 밖으로 나가지 않으면 열거에 쓰이지 않는다
		if (customer == null) {
			throw new ResponseException(Error.NOT_AUTHENTICATED, "no such customerId");
		}
		if (!customer.matchesPassword(customerSession.getCustomerPassword(), passwordEncoder)) {
			throw new ResponseException(Error.NOT_AUTHENTICATED, "password mismatch");
		}
		// 토큰 발급·쿠키 적재는 웹 관심사라 Controller가 맡는다.
		// 응답 DTO에 비밀번호 필드가 없으므로 값을 지우는 조작도 불필요하다
		return CustomerResponse.from(customer);
	}

	// 본인 확인이 없다 — 남의 계정도 고칠 수 있다 (BOLA). Phase 2에서 방어
	public CustomerResponse updateCustomer(CustomerUpdateRequest request) {
		Customer customer = findCustomer(request.getCustomerId());
		// 각 값의 유효성은 Customer가 판단한다 (음수 포인트 거부 등)
		if (request.getCustomerPassword() != null) {
			customer.changePassword(passwordEncoder.encode(request.getCustomerPassword()));
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

	/**
	 * 다른 도메인이 고객을 필요로 할 때의 진입점.
	 * <p>
	 * 엔티티를 반환하는 것은 MSA 분리 시 바뀔 지점이다 — 그때 이 호출은 원격이 되고
	 * 엔티티는 직렬화 경계를 넘지 못한다. 지금 DTO로 바꾸지 않는 이유는 호출자가 이 값을
	 * {@code OrderItem}의 연관관계로 그대로 쓰기 때문이다. 전환은 "ID 참조"와 함께 가야 한다
	 * (PLAN.md Phase 7 MSA 전환 로드맵, DECISIONS.md 4절).
	 */
	public Customer findCustomer(String customerId) {
		if (customerId == null) {
			throw new ParameterException("customerId");
		}
		return customerRepository.findByCustomerId(customerId)
				.orElseThrow(() -> new ResponseException(Error.DATA_NOT_FOUND, "Customer not found"));
	}
}
