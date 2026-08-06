package com.sk.skala.shopapi.common;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** offset/count 페이징 응답 (SPEC.md 1절). */
@Getter
@Setter
public class PagedList<T> {

	private long total;
	private int offset;
	private int count;
	private List<T> list;

	public static <T> PagedList<T> of(long total, int offset, int count, List<T> list) {
		PagedList<T> paged = new PagedList<>();
		paged.setTotal(total);
		paged.setOffset(offset);
		paged.setCount(count);
		paged.setList(list);
		return paged;
	}
}
