package com.sk.skala.shopapi.product.controller;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sk.skala.shopapi.global.common.PagedList;
import com.sk.skala.shopapi.global.common.Response;
import com.sk.skala.shopapi.product.dto.ProductRequest;
import com.sk.skala.shopapi.product.dto.ProductResponse;
import com.sk.skala.shopapi.product.service.ProductService;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "1. 상품", description = "상품 등록·조회·수정·삭제. 인증이 필요 없다")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

	private final ProductService productService;

	@Operation(summary = "상품 목록 (페이징) — offset은 페이지 번호다")
	@GetMapping("/list")
	public Response<PagedList<ProductResponse>> getAllProducts(
			@RequestParam(value = "offset", defaultValue = "0") int offset,
			@RequestParam(value = "count", defaultValue = "10") int count) {
		return Response.success(productService.getAllProducts(offset, count));
	}

	/**
	 * 상품 상세.
	 * <p>
	 * {@code Cache-Control: public, max-age=60} 을 붙인다 — 상품은 모두에게 같은 공개 데이터라
	 * 중간 캐시가 보관해도 안전하다. 60초인 이유는 <b>가격 변경이 그만큼 늦게 보여도 되는가</b>로
	 * 정했다: 이 도메인에서 가격은 자주 바뀌지 않고, 60초는 목록 새로고침 주기보다 짧다.
	 * 더 길게 잡으면 수정 후 낡은 값이 보이는 창이 그만큼 커진다 (DECISIONS.md 25절).
	 * <p>
	 * 고객·주문 응답에는 붙이지 않는다 — 개인 데이터가 중간 캐시에 남는다.
	 */
	@Operation(summary = "상품 상세")
	@GetMapping("/{id}")
	public ResponseEntity<Response<ProductResponse>> getProductById(@PathVariable Long id) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
				.body(Response.success(productService.getProductById(id)));
	}

	@Operation(summary = "상품 등록 — 요청의 id는 무시된다")
	@PostMapping
	public Response<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
		return Response.success(productService.createProduct(request));
	}

	@Operation(summary = "상품 수정 — id를 바디에 담는다 (SPEC 계약)")
	@PutMapping
	public Response<ProductResponse> updateProduct(@Valid @RequestBody ProductRequest request) {
		return Response.success(productService.updateProduct(request));
	}

	// 삭제는 id만 보내므로 @Valid를 걸지 않는다 — 걸면 productName·productPrice까지 요구하게 된다
	@Operation(summary = "상품 삭제 — 주문된 상품이면 409 DATA_IN_USE")
	@DeleteMapping
	public Response<Void> deleteProduct(@RequestBody ProductRequest request) {
		productService.deleteProduct(request);
		return Response.success();
	}
}
