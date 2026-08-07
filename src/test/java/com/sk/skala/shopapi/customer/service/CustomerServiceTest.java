package com.sk.skala.shopapi.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.sk.skala.shopapi.customer.dto.CustomerRequest;
import com.sk.skala.shopapi.customer.dto.CustomerSession;
import com.sk.skala.shopapi.customer.entity.Customer;
import com.sk.skala.shopapi.customer.repository.CustomerRepository;
import com.sk.skala.shopapi.global.exception.Error;
import com.sk.skala.shopapi.global.exception.ResponseException;

/** DB 없이 도는 Service 단위 테스트. 분기 판단만 본다 — 쿼리 동작은 Repository 테스트의 몫이다. */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

	@Mock private CustomerRepository customerRepository;
	@Mock private PasswordEncoder passwordEncoder;
	@InjectMocks private CustomerService customerService;

	private static Customer customer(String id) {
		return Customer.register(id, "encoded", new BigDecimal("1000000.00"));
	}

	@Nested
	@DisplayName("createCustomer")
	class Create {

		@Test
		void 평문은_저장되지_않고_해시만_넘어간다() {
			CustomerRequest request = new CustomerRequest();
			request.setCustomerId("skala01");
			request.setCustomerPassword("pw1234");
			given(passwordEncoder.encode("pw1234")).willReturn("$2a$10$hashed");
			given(customerRepository.existsByCustomerId("skala01")).willReturn(false);
			given(customerRepository.save(any(Customer.class))).willAnswer(i -> i.getArgument(0));

			customerService.createCustomer(request);

			then(customerRepository).should().save(
					org.mockito.ArgumentMatchers.argThat(c -> "$2a$10$hashed".equals(c.getCustomerPassword())));
		}

		@Test
		void 중복_아이디는_DATA_DUPLICATED() {
			CustomerRequest request = new CustomerRequest();
			request.setCustomerId("skala01");
			request.setCustomerPassword("pw1234");
			given(passwordEncoder.encode(anyString())).willReturn("hash");
			given(customerRepository.existsByCustomerId("skala01")).willReturn(true);

			assertThatThrownBy(() -> customerService.createCustomer(request))
					.isInstanceOf(ResponseException.class)
					.extracting(e -> ((ResponseException) e).getError())
					.isEqualTo(Error.DATA_DUPLICATED);

			then(customerRepository).should(never()).save(any());
		}
	}

	@Nested
	@DisplayName("loginCustomer — 사용자 열거 방어")
	class Login {

		private CustomerSession session(String id, String pw) {
			CustomerSession session = new CustomerSession();
			session.setCustomerId(id);
			session.setCustomerPassword(pw);
			return session;
		}

		@Test
		void 없는_아이디도_틀린_비밀번호도_모두_NOT_AUTHENTICATED() {
			// 404와 401로 갈리면 공격자가 응답만 보고 '유효한 ID 목록'을 만들 수 있다.
			// 이 테스트가 지키는 것은 '실패했다'가 아니라 '두 실패가 구별되지 않는다'이다
			given(customerRepository.findByCustomerId("nobody")).willReturn(Optional.empty());
			Customer existing = customer("skala01");
			given(customerRepository.findByCustomerId("skala01")).willReturn(Optional.of(existing));
			given(passwordEncoder.matches("wrong", "encoded")).willReturn(false);

			Error noSuchId = catchError(() -> customerService.loginCustomer(session("nobody", "pw")));
			Error wrongPw = catchError(() -> customerService.loginCustomer(session("skala01", "wrong")));

			assertThat(noSuchId).isEqualTo(Error.NOT_AUTHENTICATED);
			assertThat(wrongPw).isEqualTo(Error.NOT_AUTHENTICATED);
			assertThat(noSuchId).as("두 실패가 구별되면 계정 존재 여부가 노출된다").isEqualTo(wrongPw);
		}

		@Test
		void 일치하면_고객_정보를_반환한다() {
			given(customerRepository.findByCustomerId("skala01")).willReturn(Optional.of(customer("skala01")));
			given(passwordEncoder.matches("pw1234", "encoded")).willReturn(true);

			assertThat(customerService.loginCustomer(session("skala01", "pw1234")).getCustomerId())
					.isEqualTo("skala01");
		}

		private Error catchError(Runnable action) {
			try {
				action.run();
				throw new AssertionError("예외가 발생하지 않았다");
			} catch (ResponseException e) {
				return e.getError();
			}
		}
	}

	@Nested
	@DisplayName("requireOwner — BOLA 방어")
	class RequireOwner {

		@Test
		void 남의_식별자면_NOT_OWNER() {
			assertThatThrownBy(() -> customerService.requireOwner("skala01", "skala02"))
					.isInstanceOf(ResponseException.class)
					.extracting(e -> ((ResponseException) e).getError())
					.isEqualTo(Error.NOT_OWNER);
		}

		@Test
		void 본인이면_통과한다() {
			assertThatCode(() -> customerService.requireOwner("skala01", "skala01"))
					.doesNotThrowAnyException();
		}

		@Test
		void 대상_존재_확인보다_소유권을_먼저_본다() {
			// 순서를 바꾸면 남의 계정에 404, 없는 계정에 403이 나가 존재 여부가 노출된다.
			// findByCustomerId를 아예 호출하지 않는 것이 그 증거다
			CustomerRequest request = new CustomerRequest();
			request.setCustomerId("someone-else");

			assertThatThrownBy(() -> customerService.deleteCustomer("skala01", request))
					.isInstanceOf(ResponseException.class);

			then(customerRepository).should(never()).findByCustomerId(anyString());
		}
	}

	@Nested
	@DisplayName("deleteCustomer")
	class Delete {

		@Test
		void 보유_상품이_있으면_DATA_IN_USE() {
			// 자동 삭제하면 환불 없이 보유 상품만 사라진다. 먼저 취소하게 하면 포인트를 돌려받고 나간다
			CustomerRequest request = new CustomerRequest();
			request.setCustomerId("skala01");
			given(customerRepository.findByCustomerId("skala01")).willReturn(Optional.of(customer("skala01")));
			org.mockito.BDDMockito.willThrow(new DataIntegrityViolationException("fk"))
					.given(customerRepository).flush();

			assertThatThrownBy(() -> customerService.deleteCustomer("skala01", request))
					.isInstanceOf(ResponseException.class)
					.extracting(e -> ((ResponseException) e).getError())
					.isEqualTo(Error.DATA_IN_USE);
		}
	}

	@Nested
	@DisplayName("findCustomer")
	class Find {

		@Test
		void 없으면_DATA_NOT_FOUND() {
			given(customerRepository.findByCustomerId("nobody")).willReturn(Optional.empty());

			assertThatThrownBy(() -> customerService.findCustomer("nobody"))
					.isInstanceOf(ResponseException.class)
					.extracting(e -> ((ResponseException) e).getError())
					.isEqualTo(Error.DATA_NOT_FOUND);
		}
	}
}
