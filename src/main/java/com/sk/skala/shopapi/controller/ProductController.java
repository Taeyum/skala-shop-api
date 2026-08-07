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

import com.sk.skala.shopapi.common.PagedList;
import com.sk.skala.shopapi.common.Response;
import com.sk.skala.shopapi.data.dto.ProductRequest;
import com.sk.skala.shopapi.data.dto.ProductResponse;
import com.sk.skala.shopapi.service.ProductService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

	private final ProductService productService;

	@GetMapping("/list")
	public Response<PagedList<ProductResponse>> getAllProducts(
			@RequestParam(value = "offset", defaultValue = "0") int offset,
			@RequestParam(value = "count", defaultValue = "10") int count) {
		return Response.success(productService.getAllProducts(offset, count));
	}

	@GetMapping("/{id}")
	public Response<ProductResponse> getProductById(@PathVariable Long id) {
		return Response.success(productService.getProductById(id));
	}

	@PostMapping
	public Response<ProductResponse> createProduct(@RequestBody ProductRequest request) {
		return Response.success(productService.createProduct(request));
	}

	@PutMapping
	public Response<ProductResponse> updateProduct(@RequestBody ProductRequest request) {
		return Response.success(productService.updateProduct(request));
	}

	@DeleteMapping
	public Response<Void> deleteProduct(@RequestBody ProductRequest request) {
		productService.deleteProduct(request);
		return Response.success();
	}
}
