package com.sk.skala.shopapi.global.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * HTTP 캐시 — 상품 조회에만 건다.
 * <p>
 * <b>ETag가 아끼는 것과 아끼지 못하는 것</b>을 구분해야 한다.
 * {@code ShallowEtagHeaderFilter}는 <b>응답 본문을 다 만든 뒤</b> 해시를 내고 비교한다.
 * 따라서 <b>DB 조회도 직렬화도 그대로 일어나고</b>, 절약되는 것은 <b>네트워크 전송</b>뿐이다.
 * 서버 부하를 줄이려면 애플리케이션 캐시({@code @Cacheable})가 필요하고, 둘은 목적이 다르다.
 * <p>
 * <b>그 구분이 판단으로 이어졌다.</b> 두 기능을 각각 측정한 결과(2026-08-08),
 * ETag는 <b>전송량 −51%</b>(7.4MB → 3.6MB)로 이득이 확인됐고 낡은 값 문제도 없어 남겼다.
 * 반면 {@code @Cacheable}은 <b>이득이 측정되지 않아</b>(포화 조건에서도 p95 효과 0)
 * 다중 인스턴스 낡은 값 결함만 남기고 있어 <b>제거했다</b>.
 * 같은 "캐싱"이라도 아끼는 대상이 다르므로 판단도 갈린다 ({@code DECISIONS.md} 25절).
 * <p>
 * <b>고객·주문 경로에는 걸지 않는다.</b> 개인 데이터라 중간 캐시(프록시·CDN)가 보관하면
 * 다른 사용자에게 노출될 수 있다. 상품은 모두에게 같은 공개 데이터라 안전하다.
 */
@Configuration
public class HttpCacheConfig {

	@Bean
	public FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
		FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
				new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
		// 상품 경로만. 인증이 필요한 경로에 걸면 개인 데이터에 ETag가 붙는다
		registration.addUrlPatterns("/api/products/*");
		registration.setName("etagFilter");
		return registration;
	}
}
