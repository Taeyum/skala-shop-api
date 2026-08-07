package com.sk.skala.shopapi.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.sk.skala.shopapi.common.PagedList;
import com.sk.skala.shopapi.data.dto.ProductRequest;
import com.sk.skala.shopapi.data.dto.ProductResponse;
import com.sk.skala.shopapi.data.table.Product;
import com.sk.skala.shopapi.exception.Error;
import com.sk.skala.shopapi.exception.ParameterException;
import com.sk.skala.shopapi.exception.ResponseException;
import com.sk.skala.shopapi.repository.ProductRepository;

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

	public ProductResponse updateProduct(ProductRequest request) {
		if (request.getId() == null) {
			throw new ParameterException("id");
		}
		Product found = findProduct(request.getId());
		found.changeInfo(request.getProductName(), request.getProductPrice());
		return ProductResponse.from(productRepository.save(found));
	}

	public void deleteProduct(ProductRequest request) {
		if (request.getId() == null) {
			throw new ParameterException("id");
		}
		productRepository.delete(findProduct(request.getId()));
	}

	/** 다른 도메인(Customer)에서 상품이 필요할 때 쓰는 진입점. Repository를 직접 물지 않게 한다 */
	public Product findProduct(Long id) {
		return productRepository.findById(id)
				.orElseThrow(() -> new ResponseException(Error.DATA_NOT_FOUND, "Product not found"));
	}
}
