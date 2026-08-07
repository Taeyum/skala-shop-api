package com.sk.skala.shopapi.global.exception;

/** 필수값 누락·형식 오류. */
public class ParameterException extends RuntimeException {

	public ParameterException(String name) {
		super("invalid parameter: " + name);
	}
}
