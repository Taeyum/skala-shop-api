package com.sk.skala.shopapi.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.sk.skala.shopapi.common.PagedList;
import com.sk.skala.shopapi.common.Response;
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

	public Response<PagedList<Product>> getAllProducts(int offset, int count) {
		// offset을 페이지 번호로 해석한다 (Spring Data PageRequest 관례).
		// 강의 자료가 PageRequest 인자를 명시하지 않아 확정하지 못했다 — DECISIONS.md 8절
		Page<Product> page = productRepository.findAll(PageRequest.of(offset, count));
		return Response.success(
				PagedList.of(page.getTotalElements(), offset, count, page.getContent()));
	}

	public Response<Product> getProductById(Long id) {
		return Response.success(findProduct(id));
	}

	public Response<Product> createProduct(Product product) {
		validate(product);
		if (productRepository.existsByProductName(product.getProductName())) {
			throw new ResponseException(Error.DATA_DUPLICATED);
		}
		// 자료는 "신규 Product의 ID를 0L로 세팅"하라고 하지만 따르지 않는다.
		// Long 래퍼 필드에서 0L은 non-null이라 isNew()가 false가 되어 merge 경로를 타고
		// 불필요한 SELECT가 추가된다 — 측정 근거는 docs/evidence/ (DECISIONS.md 9절)
		return Response.success(productRepository.save(product));
	}

	public Response<Product> updateProduct(Product product) {
		if (product.getId() == null) {
			throw new ParameterException("id");
		}
		validate(product);
		Product found = findProduct(product.getId());
		found.setProductName(product.getProductName());
		found.setProductPrice(product.getProductPrice());
		return Response.success(productRepository.save(found));
	}

	public Response<Void> deleteProduct(Product product) {
		if (product.getId() == null) {
			throw new ParameterException("id");
		}
		productRepository.delete(findProduct(product.getId()));
		return Response.success();
	}

	private Product findProduct(Long id) {
		return productRepository.findById(id)
				.orElseThrow(() -> new ResponseException(Error.DATA_NOT_FOUND));
	}

	/** 상품명이 비어있거나 가격이 0 이하면 거부한다. */
	private void validate(Product product) {
		if (StringUtil.isAnyEmpty(product.getProductName())
				|| product.getProductPrice() == null
				|| product.getProductPrice() <= 0) {
			throw new ParameterException("productName, productPrice");
		}
	}
}
