package com.sk.skala.shopapi.order.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.sk.skala.shopapi.customer.entity.Customer;
import com.sk.skala.shopapi.global.exception.Error;
import com.sk.skala.shopapi.global.exception.ParameterException;
import com.sk.skala.shopapi.global.exception.ResponseException;
import com.sk.skala.shopapi.product.entity.Product;

/** Spring 없이 도는 순수 단위 테스트. 환불 계산과 반올림 규칙이 전부 이 엔티티 안에 있다. */
class OrderItemTest {

	private static final Customer CUSTOMER =
			Customer.register("skala01", "pw", new BigDecimal("1000000.00"));

	private static Product product(String price) {
		return Product.of("무선마우스", new BigDecimal(price));
	}

	/** 수량 q개를 단가 price로 산 상태 */
	private static OrderItem bought(int quantity, String unitPrice) {
		BigDecimal total = new BigDecimal(unitPrice).multiply(BigDecimal.valueOf(quantity));
		return OrderItem.of(CUSTOMER, product(unitPrice), quantity, total);
	}

	@Nested
	@DisplayName("addOrder — 수량과 결제액이 함께 움직인다")
	class AddOrder {

		@Test
		void 재주문은_수량과_총액을_누적한다() {
			OrderItem item = bought(2, "15000.00");

			item.addOrder(3, new BigDecimal("45000.00"));

			assertThat(item.getQuantity()).isEqualTo(5);
			// 이름이 addQuantity가 아닌 이유 — 총액이 같이 움직이지 않으면
			// 환불 총액이 결제 총액과 어긋난다
			assertThat(item.getOrderedAmount()).usingComparator(BigDecimal::compareTo)
					.isEqualTo(new BigDecimal("75000.00"));
		}

		@Test
		void 가격이_바뀐_뒤_재주문해도_결제_시점_금액이_누적된다() {
			// 스냅샷이 '총액'인 이유 — 병합된 행에서는 단가로 결제 총액을 복원할 수 없다
			OrderItem item = bought(1, "15000.00");

			item.addOrder(1, new BigDecimal("20000.00"));   // 인상 후 재주문

			assertThat(item.getOrderedAmount()).usingComparator(BigDecimal::compareTo)
					.isEqualTo(new BigDecimal("35000.00"));
		}

		@Test
		void 음수나_영_수량은_거부한다() {
			OrderItem item = bought(2, "15000.00");

			assertThatThrownBy(() -> item.addOrder(0, BigDecimal.TEN))
					.isInstanceOf(ParameterException.class);
			assertThatThrownBy(() -> item.addOrder(-1, BigDecimal.TEN))
					.isInstanceOf(ParameterException.class);
		}
	}

	@Nested
	@DisplayName("cancel")
	class Cancel {

		@Test
		void 전량_취소는_결제_총액_전부를_환불한다() {
			OrderItem item = bought(2, "15000.00");

			BigDecimal refund = item.cancel(2);

			assertThat(refund).usingComparator(BigDecimal::compareTo)
					.isEqualTo(new BigDecimal("30000.00"));
			assertThat(item.isEmpty()).isTrue();
			assertThat(item.getOrderedAmount()).usingComparator(BigDecimal::compareTo)
					.isEqualTo(BigDecimal.ZERO);
		}

		@Test
		void 부분_취소는_수량과_총액을_함께_줄인다() {
			OrderItem item = bought(2, "15000.00");

			BigDecimal refund = item.cancel(1);

			assertThat(refund).usingComparator(BigDecimal::compareTo)
					.isEqualTo(new BigDecimal("15000.00"));
			assertThat(item.getQuantity()).isEqualTo(1);
			assertThat(item.getOrderedAmount()).usingComparator(BigDecimal::compareTo)
					.isEqualTo(new BigDecimal("15000.00"));
			assertThat(item.isEmpty()).isFalse();
		}

		@Test
		void 보유_수량을_초과하면_INSUFFICIENT_QUANTITY() {
			OrderItem item = bought(2, "15000.00");

			assertThatThrownBy(() -> item.cancel(3))
					.isInstanceOf(ResponseException.class)
					.extracting(e -> ((ResponseException) e).getError())
					.isEqualTo(Error.INSUFFICIENT_QUANTITY);

			assertThat(item.getQuantity()).isEqualTo(2);
		}

		@Test
		void 음수_취소는_거부한다() {
			// 음수 취소는 수량을 늘리고 환불액을 음수로 만든다 — 취소가 주문이 되어버린다.
			// 실제로 2개 보유 상태에서 -10 취소가 수량을 12로 부풀렸다
			OrderItem item = bought(2, "15000.00");

			assertThatThrownBy(() -> item.cancel(-10))
					.isInstanceOf(ParameterException.class);

			assertThat(item.getQuantity()).isEqualTo(2);
		}
	}

	@Nested
	@DisplayName("반올림 — 나눠떨어지지 않는 조합에서 원금이 정확히 복귀하는가")
	class Rounding {

		@Test
		void 팔만오천원_7개를_한_개씩_7번_취소하면_원금이_정확히_돌아온다() {
			// 85,000 / 7 = 12,142.857... — 어떤 반올림을 써도 개별 환불은 딱 떨어지지 않는다.
			// 남은 총액을 '재계산'하면 반올림이 매번 새로 일어나 오차가 누적된다.
			// '차감'하면 잔여가 잔액에 남아 마지막 취소가 그것을 함께 가져간다
			OrderItem item = OrderItem.of(CUSTOMER, product("12142.86"), 7, new BigDecimal("85000.00"));

			BigDecimal total = BigDecimal.ZERO;
			for (int i = 0; i < 7; i++) {
				total = total.add(item.cancel(1));
			}

			assertThat(total).usingComparator(BigDecimal::compareTo)
					.as("7회 환불의 합이 결제 총액과 정확히 같아야 한다")
					.isEqualTo(new BigDecimal("85000.00"));
			assertThat(item.isEmpty()).isTrue();
			assertThat(item.getOrderedAmount()).usingComparator(BigDecimal::compareTo)
					.isEqualTo(BigDecimal.ZERO);
		}

		@Test
		void 개별_환불액은_정확한_몫을_넘지_않는다() {
			// DOWN(내림)을 쓰는 이유 — HALF_UP이면 개별 환불이 몫보다 커질 수 있고,
			// 그만큼 마지막 취소에서 환불액이 모자라게 된다
			OrderItem item = OrderItem.of(CUSTOMER, product("12142.86"), 7, new BigDecimal("85000.00"));

			BigDecimal refund = item.cancel(1);

			assertThat(refund).usingComparator(BigDecimal::compareTo)
					.isEqualTo(new BigDecimal("12142.85"));   // 12142.857... 내림
			assertThat(refund.multiply(BigDecimal.valueOf(7)))
					.usingComparator(BigDecimal::compareTo)
					.as("개별 환불 × 수량이 총액을 넘지 않는다")
					.isLessThanOrEqualTo(new BigDecimal("85000.00"));
		}

		@Test
		void 부분_취소를_섞어도_원금이_정확히_돌아온다() {
			OrderItem item = OrderItem.of(CUSTOMER, product("12142.86"), 7, new BigDecimal("85000.00"));

			BigDecimal total = item.cancel(3).add(item.cancel(2)).add(item.cancel(2));

			assertThat(total).usingComparator(BigDecimal::compareTo)
					.isEqualTo(new BigDecimal("85000.00"));
			assertThat(item.isEmpty()).isTrue();
		}
	}
}
