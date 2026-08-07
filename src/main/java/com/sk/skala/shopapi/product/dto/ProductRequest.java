package com.sk.skala.shopapi.product.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 상품 등록·수정·삭제 요청.
 * <p>
 * `id`는 수정·삭제에만 쓰고 <b>등록에서는 무시한다</b> — 서버가 채울 값을 클라이언트가 정하게 두지 않는다.
 * 엔티티를 그대로 받던 때는 등록 요청에 id를 실어 보내면 JPA가 merge 경로를 타
 * 불필요한 SELECT가 발생했다 (`docs/evidence/product-id-0L-vs-null.md`).
 */
@Getter
@Setter
public class ProductRequest {

	private Long id;
	private String productName;
	private BigDecimal productPrice;
}
