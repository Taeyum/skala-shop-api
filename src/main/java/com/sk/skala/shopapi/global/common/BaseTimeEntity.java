package com.sk.skala.shopapi.global.common;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * 생성·수정 시각. 상속해서 쓴다.
 * <p>
 * <b>왜 필요한가</b> — 지금은 "언제 만들어졌는지" 알 방법이 없다. 주문이 언제 들어왔는지,
 * 고객이 언제 가입했는지가 데이터에 남지 않아 장애 조사도 감사도 불가능하다.
 * 애플리케이션 로그는 보존 기간이 짧고 DB와 함께 옮겨지지도 않는다.
 * <p>
 * <b>setter가 없다.</b> {@code @CreatedDate}·{@code @LastModifiedDate}는
 * {@code AuditingEntityListener}가 리플렉션으로 채우므로 setter가 필요 없다 —
 * 엔티티에 setter를 두지 않는다는 규칙(ArchUnit이 강제)을 지키면서 감사 필드를 얻는다.
 * <p>
 * {@code updatable = false}로 생성 시각을 못 박는다. 이 컬럼은 UPDATE 문에 아예 포함되지 않아
 * <b>더티 체킹이나 잘못된 코드로도 덮어쓸 수 없다.</b>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseTimeEntity {

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(nullable = false)
	private LocalDateTime updatedAt;
}
