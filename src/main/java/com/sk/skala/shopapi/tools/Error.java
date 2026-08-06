package com.sk.skala.shopapi.tools;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 비즈니스 에러 코드 (SPEC.md 4절). HTTP 상태 매핑은 Phase 2에서 붙인다. */
@Getter
@RequiredArgsConstructor
public enum Error {

	DATA_NOT_FOUND("data not found"),
	DATA_DUPLICATED("data duplicated"),
	INSUFFICIENT_FUNDS("insufficient funds"),
	INSUFFICIENT_QUANTITY("insufficient quantity"),
	NOT_AUTHENTICATED("not authenticated");

	private final String message;
}
