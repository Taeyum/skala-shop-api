package com.sk.skala.shopapi.global.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 비즈니스 에러 코드와 HTTP 상태 매핑 (SPEC.md 4절).
 * <p>
 * 상태를 enum이 들고 있는 이유 — 핸들러에 {@code switch}를 두면 코드를 추가할 때 매핑을 빠뜨려도
 * 컴파일이 되고, 그 코드는 조용히 500으로 나간다. 코드와 상태를 한자리에 두면 빠뜨릴 수 없다.
 */
@Getter
@RequiredArgsConstructor
public enum Error {

	DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "data not found"),
	DATA_DUPLICATED(HttpStatus.CONFLICT, "data duplicated"),

	// SPEC 4절이 400으로 명시한다. 상태 충돌로 보면 409도 근거가 있으나 계약을 따른다
	// (검토 경위는 DECISIONS.md 9-3절)
	INSUFFICIENT_FUNDS(HttpStatus.BAD_REQUEST, "insufficient funds"),
	INSUFFICIENT_QUANTITY(HttpStatus.BAD_REQUEST, "insufficient quantity"),

	NOT_AUTHENTICATED(HttpStatus.UNAUTHORIZED, "not authenticated");

	private final HttpStatus status;
	private final String message;
}
