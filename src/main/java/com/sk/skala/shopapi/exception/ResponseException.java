package com.sk.skala.shopapi.exception;

import com.sk.skala.shopapi.tools.Error;

import lombok.Getter;

/** Error 코드를 담는 비즈니스 예외. */
@Getter
public class ResponseException extends RuntimeException {

	private final Error error;

	public ResponseException(Error error) {
		super(error.name());
		this.error = error;
	}
}
