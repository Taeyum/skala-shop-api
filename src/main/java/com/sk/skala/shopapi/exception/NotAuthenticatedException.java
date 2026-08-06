package com.sk.skala.shopapi.exception;

/** 미로그인·토큰 무효. */
public class NotAuthenticatedException extends RuntimeException {

	public NotAuthenticatedException() {
		super("not authenticated");
	}
}
