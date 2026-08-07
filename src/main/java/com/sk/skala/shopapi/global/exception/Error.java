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

	/**
	 * 다른 데이터가 참조하고 있어 삭제할 수 없다. 409
	 * <p>
	 * 요청 자체는 올바르고 <b>현재 상태와 충돌</b>하는 경우라 409다.
	 * ({@code INSUFFICIENT_*}는 SPEC 4절이 400으로 명시해 계약을 따랐지만,
	 * 이 코드는 자료에 없어 상태 충돌 의미대로 정할 수 있었다 — 9-6절)
	 */
	DATA_IN_USE(HttpStatus.CONFLICT, "data is referenced by other records"),

	// SPEC 4절이 400으로 명시한다. 상태 충돌로 보면 409도 근거가 있으나 계약을 따른다
	// (검토 경위는 DECISIONS.md 9-3절)
	INSUFFICIENT_FUNDS(HttpStatus.BAD_REQUEST, "insufficient funds"),
	INSUFFICIENT_QUANTITY(HttpStatus.BAD_REQUEST, "insufficient quantity"),

	/** 인증 실패 — <b>누구인지</b> 확인되지 않았다. 401 */
	NOT_AUTHENTICATED(HttpStatus.UNAUTHORIZED, "not authenticated"),

	/**
	 * 인가 실패 — 누구인지는 알지만 <b>그 대상에 대한 권한</b>이 없다. 403
	 * <p>
	 * 이름을 {@code NOT_AUTHORIZED}로 하지 않았다. {@code NOT_AUTHENTICATED}와 철자가 두 글자
	 * 차이라 코드에서 눈으로 구별되지 않고, 401/403을 뒤바꿔 쓰는 실수가 정확히 그 혼동에서 나온다.
	 * {@code NOT_OWNER}는 무엇이 문제인지를 직접 말한다 — <b>요청 대상이 본인 소유가 아니다.</b>
	 * 역할(admin 등) 기반 인가가 생기면 그때 별도 코드를 추가한다.
	 */
	NOT_OWNER(HttpStatus.FORBIDDEN, "not the owner of the resource");

	private final HttpStatus status;
	private final String message;
}
