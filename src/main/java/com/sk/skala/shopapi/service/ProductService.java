package com.sk.skala.shopapi.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.sk.skala.shopapi.data.table.Product;
import com.sk.skala.shopapi.exception.ParameterException;
import com.sk.skala.shopapi.exception.ResponseException;
import com.sk.skala.shopapi.repository.ProductRepository;
import com.sk.skala.shopapi.exception.Error;
import com.sk.skala.shopapi.common.PagedList;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

	private final ProductRepository productRepository;

	public PagedList<Product> getProducts(int offset, int count) {
		// offset을 페이지 번호로 해석한다 (Spring Data PageRequest 관례).
		// SPEC이 둘 중 어느 쪽인지 못박지 않았고, E2E가 쓰는 offset=0에서는 결과가 같다
		Page<Product> page = productRepository.findAll(PageRequest.of(offset, count));
		return PagedList.of(page.getTotalElements(), offset, count, page.getContent());
	}

	public Product getProduct(Long id) {
		return productRepository.findById(id)
				.orElseThrow(() -> new ResponseException(Error.DATA_NOT_FOUND));
	}

	public Product createProduct(Product product) {
		if (product.getProductName() == null) {
			throw new ParameterException("productName");
		}
		if (productRepository.existsByProductName(product.getProductName())) {
			throw new ResponseException(Error.DATA_DUPLICATED);
		}
		return productRepository.save(product);
	}

	public Product updateProduct(Product product) {
		if (product.getId() == null) {
			throw new ParameterException("id");
		}
		Product found = getProduct(product.getId());
		found.setProductName(product.getProductName());
		found.setProductPrice(product.getProductPrice());
		return productRepository.save(found);
	}

	public void deleteProduct(Product product) {
		if (product.getId() == null) {
			throw new ParameterException("id");
		}
		productRepository.delete(getProduct(product.getId()));
	}
}
