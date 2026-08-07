package com.sk.skala.shopapi.customer.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.sk.skala.shopapi.global.exception.Error;
import com.sk.skala.shopapi.global.exception.ParameterException;
import com.sk.skala.shopapi.global.exception.ResponseException;

/**
 * <b>Spring 컨텍스트 없이 돈다.</b> 풍부한 도메인 모델의 이점이 여기서 나온다 —
 * 불변식이 엔티티 안에 있으므로 DB도 웹도 없이 검증할 수 있다.
 * 컨텍스트를 띄워야만 검증되는 로직이 있다면 그건 로직이 엔티티 밖으로 샜다는 신호다.
 */
class CustomerTest {

	private static final BigDecimal INITIAL = new BigDecimal("1000000.00");

	private Customer customer() {
		return Customer.register("skala01", "encoded-pw", INITIAL);
	}

	@Nested
	@DisplayName("register")
	class Register {

		@Test
		void 아이디나_비밀번호가_비면_거부한다() {
			assertThatThrownBy(() -> Customer.register("", "pw", INITIAL))
					.isInstanceOf(ParameterException.class);
			assertThatThrownBy(() -> Customer.register("skala01", "  ", INITIAL))
					.isInstanceOf(ParameterException.class);
			assertThatThrownBy(() -> Customer.register(null, "pw", INITIAL))
					.isInstanceOf(ParameterException.class);
		}

		@Test
		void 초기_포인트는_인자로_받은_값이_된다() {
			// 서버가 정한 값이 그대로 들어가는지 — 클라이언트가 개입할 자리가 없다
			assertThat(customer().getCustomerPoint()).usingComparator(BigDecimal::compareTo)
					.isEqualTo(INITIAL);
		}
	}

	@Nested
	@DisplayName("usePoint — 잔액 부족을 스스로 판단한다")
	class UsePoint {

		@Test
		void 잔액만큼은_차감된다() {
			Customer customer = customer();

			customer.usePoint(new BigDecimal("30000.00"));

			assertThat(customer.getCustomerPoint()).usingComparator(BigDecimal::compareTo)
					.isEqualTo(new BigDecimal("970000.00"));
		}

		@Test
		void 잔액과_정확히_같은_금액은_허용된다() {
			// 경계값 — compareTo < 0 이므로 같으면 통과해야 한다. <= 로 잘못 쓰면 여기서 깨진다
			Customer customer = customer();

			customer.usePoint(INITIAL);

			assertThat(customer.getCustomerPoint()).usingComparator(BigDecimal::compareTo)
					.isEqualTo(BigDecimal.ZERO);
		}

		@Test
		void 잔액을_1원이라도_넘으면_INSUFFICIENT_FUNDS() {
			Customer customer = customer();

			assertThatThrownBy(() -> customer.usePoint(new BigDecimal("1000000.01")))
					.isInstanceOf(ResponseException.class)
					.extracting(e -> ((ResponseException) e).getError())
					.isEqualTo(Error.INSUFFICIENT_FUNDS);
		}

		@Test
		void 실패해도_잔액은_그대로다() {
			Customer customer = customer();

			assertThatThrownBy(() -> customer.usePoint(new BigDecimal("2000000.00")))
					.isInstanceOf(ResponseException.class);

			assertThat(customer.getCustomerPoint()).usingComparator(BigDecimal::compareTo)
					.isEqualTo(INITIAL);
		}

		@Test
		void 음수_결제액은_거부한다() {
			// 음수를 허용하면 차감이 증가가 된다. Bean Validation은 웹 진입만 지키므로
			// 엔티티에서도 막아야 한다 (DECISIONS.md 9-4절)
			Customer customer = customer();

			assertThatThrownBy(() -> customer.usePoint(new BigDecimal("-1000.00")))
					.isInstanceOf(ParameterException.class);
			assertThatThrownBy(() -> customer.usePoint(BigDecimal.ZERO))
					.isInstanceOf(ParameterException.class);
			assertThatThrownBy(() -> customer.usePoint(null))
					.isInstanceOf(ParameterException.class);
		}
	}

	@Nested
	@DisplayName("refundPoint — 음수 환불이 잔액 검사를 우회하는 구멍이었다")
	class RefundPoint {

		@Test
		void 환불하면_잔액이_늘어난다() {
			Customer customer = customer();
			customer.usePoint(new BigDecimal("30000.00"));

			customer.refundPoint(new BigDecimal("15000.00"));

			assertThat(customer.getCustomerPoint()).usingComparator(BigDecimal::compareTo)
					.isEqualTo(new BigDecimal("985000.00"));
		}

		@Test
		void 음수_환불은_거부한다() {
			// 이 메서드에는 잔액 검사가 없다 — 환불은 잔액을 늘리는 연산이라 필요 없기 때문이다.
			// 그래서 음수를 허용하면 usePoint의 잔액 검사를 우회해 잔액을 음수로 만들 수 있었다
			Customer customer = customer();

			assertThatThrownBy(() -> customer.refundPoint(new BigDecimal("-9999999.00")))
					.isInstanceOf(ParameterException.class);

			assertThat(customer.getCustomerPoint()).usingComparator(BigDecimal::compareTo)
					.isEqualTo(INITIAL);
		}
	}

	@Nested
	@DisplayName("changePoint / changePassword")
	class Change {

		@Test
		void 음수_포인트로는_변경할_수_없다() {
			// Phase 0~1은 자료를 따라 DATA_NOT_FOUND(404)를 던졌다.
			// 검증 실패에 '데이터 없음'은 의미가 맞지 않아 ParameterException(400)으로 교정했다
			assertThatThrownBy(() -> customer().changePoint(new BigDecimal("-1")))
					.isInstanceOf(ParameterException.class);
		}

		@Test
		void 영_포인트는_허용된다() {
			Customer customer = customer();

			customer.changePoint(BigDecimal.ZERO);

			assertThat(customer.getCustomerPoint()).usingComparator(BigDecimal::compareTo)
					.isEqualTo(BigDecimal.ZERO);
		}

		@Test
		void 빈_비밀번호로는_변경할_수_없다() {
			assertThatThrownBy(() -> customer().changePassword("  "))
					.isInstanceOf(ParameterException.class);
			assertThatThrownBy(() -> customer().changePassword(null))
					.isInstanceOf(ParameterException.class);
		}
	}

	@Nested
	@DisplayName("matchesPassword — 무엇이 일치인지는 인코더에 위임한다")
	class MatchesPassword {

		@Test
		void 해시와_평문을_대조한다() {
			BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
			Customer customer = Customer.register("skala01", encoder.encode("pw1234"), INITIAL);

			assertThat(customer.matchesPassword("pw1234", encoder)).isTrue();
			assertThat(customer.matchesPassword("wrong", encoder)).isFalse();
		}

		@Test
		void 엔티티는_스프링_빈을_직접_물지_않는다() {
			// 인코더를 인자로 받으므로 알고리즘이 바뀌어도 이 메서드의 의미는 그대로다
			assertThatCode(() -> customer().matchesPassword("x", new BCryptPasswordEncoder()))
					.doesNotThrowAnyException();
		}
	}
}
