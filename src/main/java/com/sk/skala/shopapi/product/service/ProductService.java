package com.sk.skala.shopapi.product.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.sk.skala.shopapi.global.common.PagedList;
import com.sk.skala.shopapi.global.config.CacheConfig;
import com.sk.skala.shopapi.product.dto.ProductRequest;
import com.sk.skala.shopapi.product.dto.ProductResponse;
import com.sk.skala.shopapi.product.entity.Product;
import com.sk.skala.shopapi.global.exception.Error;
import com.sk.skala.shopapi.global.exception.ParameterException;
import com.sk.skala.shopapi.global.exception.ResponseException;
import com.sk.skala.shopapi.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

	private final ProductRepository productRepository;

	public PagedList<ProductResponse> getAllProducts(int offset, int count) {
		// offset을 페이지 번호로 해석한다 (Spring Data PageRequest 관례).
		// 강의 자료가 PageRequest 인자를 명시하지 않아 확정하지 못했다 — DECISIONS.md 8절
		Page<Product> page = productRepository.findAll(PageRequest.of(offset, count));
		return PagedList.of(page.getTotalElements(), offset, count,
				page.getContent().stream().map(ProductResponse::from).toList());
	}

	/**
	 * 개별 상품 조회 — 캐시 대상.
	 * <p>
	 * 반환 타입이 <b>엔티티가 아니라 DTO</b>인 것이 중요하다. 엔티티를 캐시하면
	 * 영속성 컨텍스트 밖의 detached 인스턴스를 여러 요청이 공유하게 되어
	 * 지연 로딩 예외나 의도치 않은 상태 공유가 생긴다.
	 */
	@Cacheable(cacheNames = CacheConfig.PRODUCT_CACHE, key = "#id")
	public ProductResponse getProductById(Long id) {
		return ProductResponse.from(findProduct(id));
	}

	public ProductResponse createProduct(ProductRequest request) {
		// 이름·가격 검증은 Product.of가 한다. request.getId()는 무시한다 —
		// 서버가 채울 값을 클라이언트가 정하게 두지 않는다
		Product product = Product.of(request.getProductName(), request.getProductPrice());
		if (productRepository.existsByProductName(product.getProductName())) {
			throw new ResponseException(Error.DATA_DUPLICATED, "Product name already exists");
		}
		return ProductResponse.from(productRepository.save(product));
	}

	// 수정·삭제 시 해당 키만 비운다. allEntries = true 로 전부 비우면
	// 상품 하나 고칠 때마다 전체 캐시가 날아가 적중률이 무너진다
	@CacheEvict(cacheNames = CacheConfig.PRODUCT_CACHE, key = "#request.id")
	public ProductResponse updateProduct(ProductRequest request) {
		if (request.getId() == null) {
			throw new ParameterException("id");
		}
		Product found = findProduct(request.getId());
		found.changeInfo(request.getProductName(), request.getProductPrice());
		return ProductResponse.from(productRepository.save(found));
	}

	@CacheEvict(cacheNames = CacheConfig.PRODUCT_CACHE, key = "#request.id")
	public void deleteProduct(ProductRequest request) {
		if (request.getId() == null) {
			throw new ParameterException("id");
		}
		Product product = findProduct(request.getId());
		try {
			productRepository.delete(product);
			// 삭제를 지금 DB에 보낸다 — flush하지 않으면 FK 위반이 트랜잭션 커밋 시점에 터져
			// 이 try 블록 밖으로 나가고, 번역되지 못한 채 500이 된다
			productRepository.flush();
		} catch (DataIntegrityViolationException e) {
			// 주문한 고객이 있는지 확인하려면 order 도메인의 Repository를 봐야 하는데
			// product → order 의존은 순환을 만든다 (order → product가 이미 있다).
			// 참조 무결성의 authority는 DB이므로 DB가 거부한 것을 번역한다 (DECISIONS.md 9-6절)
			throw new ResponseException(Error.DATA_IN_USE, "product is ordered by customers");
		}
	}

	/** 다른 도메인(Customer)에서 상품이 필요할 때 쓰는 진입점. Repository를 직접 물지 않게 한다 */
	public Product findProduct(Long id) {
		return productRepository.findById(id)
				.orElseThrow(() -> new ResponseException(Error.DATA_NOT_FOUND, "Product not found"));
	}
}
