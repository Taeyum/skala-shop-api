package com.sk.skala.shopapi.global.exception;

import lombok.Getter;

/** Error 코드를 담는 비즈니스 예외. */
@Getter
public class ResponseException extends RuntimeException {

	private final Error error;

	public ResponseException(Error error) {
		this(error, null);
	}

	/**
	 * 같은 Error 코드라도 어느 대상에서 났는지 구분하기 위한 맥락 메시지.
	 * 응답 바디에는 코드만 나가고 이 메시지는 로그에만 남는다 — 내부 사정을 밖으로 흘리지 않는다.
	 */
	public ResponseException(Error error, String detail) {
		super(detail == null ? error.name() : error.name() + ": " + detail);
		this.error = error;
	}
}
