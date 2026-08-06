package com.sk.skala.shopapi.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sk.skala.shopapi.data.table.Product;
import com.sk.skala.shopapi.service.ProductService;
import com.sk.skala.shopapi.common.PagedList;
import com.sk.skala.shopapi.common.Response;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

	private final ProductService productService;

	@GetMapping("/list")
	public Response<PagedList<Product>> getProducts(
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(defaultValue = "10") int count) {
		return Response.success(productService.getProducts(offset, count));
	}

	@GetMapping("/{id}")
	public Response<Product> getProduct(@PathVariable Long id) {
		return Response.success(productService.getProduct(id));
	}

	// 엔티티를 그대로 요청 바디로 받는다 — Phase 1에서 Request DTO로 분리
	@PostMapping
	public Response<Product> createProduct(@RequestBody Product product) {
		return Response.success(productService.createProduct(product));
	}

	@PutMapping
	public Response<Product> updateProduct(@RequestBody Product product) {
		return Response.success(productService.updateProduct(product));
	}

	@DeleteMapping
	public Response<Void> deleteProduct(@RequestBody Product product) {
		productService.deleteProduct(product);
		return Response.success();
	}
}
