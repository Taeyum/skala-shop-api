package com.sk.skala.shopapi.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.sk.skala.shopapi.global.exception.Error;
import com.sk.skala.shopapi.global.exception.ParameterException;
import com.sk.skala.shopapi.global.exception.ResponseException;
import com.sk.skala.shopapi.product.dto.ProductRequest;
import com.sk.skala.shopapi.product.entity.Product;
import com.sk.skala.shopapi.product.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

	@Mock private ProductRepository productRepository;
	@InjectMocks private ProductService productService;

	private static ProductRequest request(Long id, String name, String price) {
		ProductRequest request = new ProductRequest();
		request.setId(id);
		request.setProductName(name);
		request.setProductPrice(price == null ? null : new BigDecimal(price));
		return request;
	}

	@Test
	void 중복_상품명은_DATA_DUPLICATED() {
		given(productRepository.existsByProductName("무선마우스")).willReturn(true);

		assertThatThrownBy(() -> productService.createProduct(request(null, "무선마우스", "15000")))
				.isInstanceOf(ResponseException.class)
				.extracting(e -> ((ResponseException) e).getError())
				.isEqualTo(Error.DATA_DUPLICATED);

		then(productRepository).should(never()).save(any());
	}

	@Test
	void 생성_요청의_id는_무시된다() {
		// 서버가 채울 값을 클라이언트가 정하게 두지 않는다 (Mass Assignment)
		given(productRepository.existsByProductName(anyString())).willReturn(false);
		given(productRepository.save(any(Product.class))).willAnswer(i -> i.getArgument(0));

		productService.createProduct(request(999L, "새상품", "1000"));

		then(productRepository).should().save(org.mockito.ArgumentMatchers.argThat(
				p -> p.getId() == null));
	}

	@Test
	void 없는_상품_조회는_DATA_NOT_FOUND() {
		given(productRepository.findById(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> productService.getProductById(99L))
				.isInstanceOf(ResponseException.class)
				.extracting(e -> ((ResponseException) e).getError())
				.isEqualTo(Error.DATA_NOT_FOUND);
	}

	@Test
	void 수정_삭제에_id가_없으면_ParameterException() {
		assertThatThrownBy(() -> productService.updateProduct(request(null, "x", "1000")))
				.isInstanceOf(ParameterException.class);
		assertThatThrownBy(() -> productService.deleteProduct(request(null, null, null)))
				.isInstanceOf(ParameterException.class);
	}

	@Test
	void 주문된_상품_삭제는_DATA_IN_USE() {
		// 참조 무결성의 authority는 DB다. DB가 거부한 것을 번역한다 —
		// order 도메인 Repository를 보면 product → order 순환이 된다
		Product product = Product.of("무선마우스", new BigDecimal("15000"));
		given(productRepository.findById(1L)).willReturn(Optional.of(product));
		org.mockito.BDDMockito.willThrow(new DataIntegrityViolationException("fk"))
				.given(productRepository).flush();

		assertThatThrownBy(() -> productService.deleteProduct(request(1L, null, null)))
				.isInstanceOf(ResponseException.class)
				.extracting(e -> ((ResponseException) e).getError())
				.isEqualTo(Error.DATA_IN_USE);
	}

	@Test
	void 수정은_기존_엔티티의_상태를_바꾼다() {
		Product product = Product.of("무선마우스", new BigDecimal("15000"));
		given(productRepository.findById(1L)).willReturn(Optional.of(product));
		given(productRepository.save(any(Product.class))).willAnswer(i -> i.getArgument(0));

		productService.updateProduct(request(1L, "유선마우스", "9000"));

		assertThat(product.getProductName()).isEqualTo("유선마우스");
	}
}
