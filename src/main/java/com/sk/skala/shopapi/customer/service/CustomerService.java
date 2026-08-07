package com.sk.skala.shopapi.customer.service;

import java.math.BigDecimal;

import org.springframework.dao.DataIntegrityViolationException;
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
		// 형태 검사(빈 값 여부)는 Controller의 @Valid + CustomerSession의 @NotBlank가 끝냈다.
		// 6단계에서 "Phase 2 Bean Validation에서 Controller로 올라간다"고 예고한 항목이다.
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

	public CustomerResponse updateCustomer(String loginCustomerId, CustomerUpdateRequest request) {
		requireOwner(loginCustomerId, request.getCustomerId());
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

	/**
	 * 탈퇴. <b>보유 중인 상품이 있으면 거부한다.</b>
	 * <p>
	 * 주문을 함께 지우는 방식도 검토했으나, 이 구조에서는 고객에게 손해다 —
	 * {@code OrderItem}은 "주문 이력"이 아니라 "현재 보유 수량"이고, 취소해야 포인트가 환불된다.
	 * 자동 삭제하면 <b>환불 없이 보유 상품만 사라진다.</b> 먼저 취소하게 하면 포인트를 돌려받고 나간다.
	 * (구현상으로도 customer → order 의존이 필요해 순환이 된다 — DECISIONS.md 9-6절)
	 */
	public void deleteCustomer(String loginCustomerId, CustomerRequest request) {
		requireOwner(loginCustomerId, request.getCustomerId());
		Customer customer = findCustomer(request.getCustomerId());
		try {
			customerRepository.delete(customer);
			customerRepository.flush();
		} catch (DataIntegrityViolationException e) {
			throw new ResponseException(Error.DATA_IN_USE, "customer still holds ordered products");
		}
	}

	/**
	 * BOLA(Broken Object Level Authorization) 방어 — OWASP API Security Top 10 #1.
	 * <p>
	 * 인증만으로는 부족하다. 로그인한 사용자가 <b>남의 식별자</b>를 바디에 넣어 보내면
	 * 그대로 통했다. 인증(누구인가)과 인가(그 대상에 권한이 있는가)는 다른 검사다.
	 * <p>
	 * 대상이 존재하는지 <b>확인하기 전에</b> 소유권을 본다 — 순서를 바꾸면 남의 계정에 대해
	 * 404와 403이 갈려 계정 존재 여부가 노출된다 (9-3절 사용자 열거와 같은 형태).
	 */
	public void requireOwner(String loginCustomerId, String targetCustomerId) {
		if (!loginCustomerId.equals(targetCustomerId)) {
			throw new ResponseException(Error.NOT_OWNER,
					"login=" + loginCustomerId + " target=" + targetCustomerId);
		}
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
