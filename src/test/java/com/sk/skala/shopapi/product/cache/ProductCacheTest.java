package com.sk.skala.shopapi.product.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.sk.skala.shopapi.global.config.CacheConfig;
import com.sk.skala.shopapi.product.dto.ProductRequest;
import com.sk.skala.shopapi.product.entity.Product;
import com.sk.skala.shopapi.product.repository.ProductRepository;
import com.sk.skala.shopapi.product.service.ProductService;
import com.sk.skala.shopapi.support.PostgresTestContainer;

/**
 * 캐시는 <b>틀린 값을 빠르게 주는</b> 실패 방식을 갖는다. 그래서 "빨라졌는가"보다
 * <b>"무효화가 실제로 되는가"</b>를 먼저 고정한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainer.class)
class ProductCacheTest {

	@Autowired private ProductService productService;
	@Autowired private ProductRepository productRepository;
	@Autowired private CacheManager cacheManager;

	private Long saveProduct(String suffix, String price) {
		return productRepository.save(
				Product.of("cache-" + System.nanoTime() + suffix, new BigDecimal(price))).getId();
	}

	private static ProductRequest request(Long id, String name, String price) {
		ProductRequest r = new ProductRequest();
		r.setId(id); r.setProductName(name); r.setProductPrice(new BigDecimal(price));
		return r;
	}

	@Test
	@DisplayName("조회하면 캐시에 올라간다")
	void 조회가_캐시에_적재된다() {
		Long id = saveProduct("a", "1000.00");

		productService.getProductById(id);

		assertThat(cacheManager.getCache(CacheConfig.PRODUCT_CACHE).get(id))
				.as("캐시에 없으면 매 조회가 DB로 간다").isNotNull();
	}

	@Test
	@DisplayName("★ 수정하면 그 키만 무효화된다 — 낡은 값을 주지 않는다")
	void 수정하면_캐시가_무효화된다() {
		Long id = saveProduct("b", "1000.00");
		Long other = saveProduct("c", "2000.00");
		productService.getProductById(id);
		productService.getProductById(other);

		productService.updateProduct(request(id, "수정된이름", "9999.00"));

		assertThat(cacheManager.getCache(CacheConfig.PRODUCT_CACHE).get(id))
				.as("무효화되지 않으면 수정 전 값을 계속 준다").isNull();
		assertThat(cacheManager.getCache(CacheConfig.PRODUCT_CACHE).get(other))
				.as("다른 키까지 비우면 상품 하나 고칠 때마다 전체 적중률이 무너진다").isNotNull();
		assertThat(productService.getProductById(id).getProductName()).isEqualTo("수정된이름");
	}

	@Test
	@DisplayName("삭제하면 캐시에서도 사라진다")
	void 삭제하면_캐시가_무효화된다() {
		Long id = saveProduct("d", "1000.00");
		productService.getProductById(id);

		productService.deleteProduct(request(id, null, "1"));

		assertThat(cacheManager.getCache(CacheConfig.PRODUCT_CACHE).get(id)).isNull();
	}
}
