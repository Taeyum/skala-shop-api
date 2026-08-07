package com.sk.skala.shopapi.product.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.sk.skala.shopapi.global.exception.ParameterException;

class ProductTest {

	@Test
	void 유효한_값이면_생성된다() {
		Product product = Product.of("무선마우스", new BigDecimal("15000.00"));

		assertThat(product.getProductName()).isEqualTo("무선마우스");
		assertThat(product.getProductPrice()).usingComparator(BigDecimal::compareTo)
				.isEqualTo(new BigDecimal("15000.00"));
	}

	@Test
	void 이름이_비면_생성되지_않는다() {
		// 생성 시점부터 유효한 상태만 허용한다 — 검증을 통과하지 못하면 객체가 만들어지지 않는다
		assertThatThrownBy(() -> Product.of("  ", new BigDecimal("15000.00")))
				.isInstanceOf(ParameterException.class);
		assertThatThrownBy(() -> Product.of(null, new BigDecimal("15000.00")))
				.isInstanceOf(ParameterException.class);
	}

	@Test
	void 가격이_0_이하이거나_null이면_생성되지_않는다() {
		assertThatThrownBy(() -> Product.of("무선마우스", BigDecimal.ZERO))
				.isInstanceOf(ParameterException.class);
		assertThatThrownBy(() -> Product.of("무선마우스", new BigDecimal("-1")))
				.isInstanceOf(ParameterException.class);
		assertThatThrownBy(() -> Product.of("무선마우스", null))
				.isInstanceOf(ParameterException.class);
	}

	@Test
	void 영_가격_비교에_scale이_영향을_주지_않는다() {
		// equals였다면 0 != 0.00이라 "0.00원"이 통과했을 것이다. compareTo를 쓴 이유
		assertThatThrownBy(() -> Product.of("무선마우스", new BigDecimal("0.00")))
				.isInstanceOf(ParameterException.class);
	}

	@Test
	void changeInfo도_같은_불변식을_적용한다() {
		Product product = Product.of("무선마우스", new BigDecimal("15000.00"));

		assertThatThrownBy(() -> product.changeInfo("", new BigDecimal("100")))
				.isInstanceOf(ParameterException.class);

		product.changeInfo("유선마우스", new BigDecimal("9000.00"));
		assertThat(product.getProductName()).isEqualTo("유선마우스");
	}
}
