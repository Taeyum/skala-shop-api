package com.sk.skala.shopapi.service;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.sk.skala.shopapi.common.PagedList;
import com.sk.skala.shopapi.common.Response;
import com.sk.skala.shopapi.data.dto.ProductRequest;
import com.sk.skala.shopapi.data.dto.ProductResponse;
import com.sk.skala.shopapi.data.table.Product;
import com.sk.skala.shopapi.exception.Error;
import com.sk.skala.shopapi.exception.ParameterException;
import com.sk.skala.shopapi.exception.ResponseException;
import com.sk.skala.shopapi.repository.ProductRepository;
import com.sk.skala.shopapi.tools.StringUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

	private final ProductRepository productRepository;

	public Response<PagedList<ProductResponse>> getAllProducts(int offset, int count) {
		// offset을 페이지 번호로 해석한다 (Spring Data PageRequest 관례).
		// 강의 자료가 PageRequest 인자를 명시하지 않아 확정하지 못했다 — DECISIONS.md 8절
		Page<Product> page = productRepository.findAll(PageRequest.of(offset, count));
		return Response.success(PagedList.of(page.getTotalElements(), offset, count,
				page.getContent().stream().map(ProductResponse::from).toList()));
	}

	public Response<ProductResponse> getProductById(Long id) {
		return Response.success(ProductResponse.from(findProduct(id)));
	}

	public Response<ProductResponse> createProduct(ProductRequest request) {
		validate(request);
		if (productRepository.existsByProductName(request.getProductName())) {
			throw new ResponseException(Error.DATA_DUPLICATED, "Product name already exists");
		}
		// request.getId()는 무시한다 — 서버가 채울 값을 클라이언트가 정하게 두지 않는다
		Product product = new Product();
		product.setProductName(request.getProductName());
		product.setProductPrice(request.getProductPrice());
		return Response.success(ProductResponse.from(productRepository.save(product)));
	}

	public Response<ProductResponse> updateProduct(ProductRequest request) {
		if (request.getId() == null) {
			throw new ParameterException("id");
		}
		validate(request);
		Product found = findProduct(request.getId());
		found.setProductName(request.getProductName());
		found.setProductPrice(request.getProductPrice());
		return Response.success(ProductResponse.from(productRepository.save(found)));
	}

	public Response<Void> deleteProduct(ProductRequest request) {
		if (request.getId() == null) {
			throw new ParameterException("id");
		}
		productRepository.delete(findProduct(request.getId()));
		return Response.success();
	}

	/** 다른 도메인(Customer)에서 상품이 필요할 때 쓰는 진입점. Repository를 직접 물지 않게 한다 */
	public Product findProduct(Long id) {
		return productRepository.findById(id)
				.orElseThrow(() -> new ResponseException(Error.DATA_NOT_FOUND, "Product not found"));
	}

	/** 상품명이 비어있거나 가격이 0 이하면 거부한다. */
	private void validate(ProductRequest request) {
		// BigDecimal 비교는 compareTo — equals는 scale까지 보므로 0 != 0.00이 된다
		if (StringUtil.isAnyEmpty(request.getProductName())
				|| request.getProductPrice() == null
				|| request.getProductPrice().compareTo(BigDecimal.ZERO) <= 0) {
			throw new ParameterException("productName, productPrice");
		}
	}
}
