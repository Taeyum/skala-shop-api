package com.sk.skala.shopapi.global.common;

import lombok.Getter;
import lombok.Setter;

/** 모든 API 응답을 감싸는 공통 객체 (SPEC.md 2절). */
@Getter
@Setter
public class Response<T> {

	public static final String SUCCESS = "success";
	public static final String FAIL = "fail";

	private String result;
	private String message;
	private T body;

	public static <T> Response<T> success(T body) {
		Response<T> response = new Response<>();
		response.setResult(SUCCESS);
		response.setBody(body);
		return response;
	}

	public static <T> Response<T> success() {
		return success(null);
	}

	public static <T> Response<T> fail(String message) {
		Response<T> response = new Response<>();
		response.setResult(FAIL);
		response.setMessage(message);
		return response;
	}
}
