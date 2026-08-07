package com.sk.skala.shopapi.global.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * 상품 조회 캐싱.
 * <p>
 * <b>왜 상품만인가</b> — 상품은 <b>읽기가 압도적으로 많고 쓰기가 드물며 모든 사용자에게 같다.</b>
 * 고객·주문은 사용자마다 다르고 주문할 때마다 바뀌므로 캐시 적중률이 낮고 무효화가 복잡하다.
 * 캐시는 "많이 읽고 적게 쓰고 모두에게 같은" 데이터에만 이득이다.
 * <p>
 * <b>구현은 {@code ConcurrentMapCacheManager}(기본값)를 쓴다.</b> 인메모리 로컬 캐시다 —
 * 인스턴스가 여럿이면 <b>인스턴스마다 다른 캐시를 갖는다.</b> 상품 수정이 한 인스턴스에서
 * 일어나면 다른 인스턴스는 낡은 값을 계속 준다. Redis 같은 공유 캐시가 정답이지만
 * 이 프로젝트에 캐시 서버를 추가하는 것은 <b>측정으로 정당화되지 않는 복잡도</b>다.
 * 한계를 문서에 남기고 로컬 캐시를 쓴다 (DECISIONS.md 25절).
 * <p>
 * {@code SPRING_CACHE_TYPE=none} 으로 끌 수 있다 — 캐시 전/후 비교 측정을 위해서다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

	public static final String PRODUCT_CACHE = "products";
}
