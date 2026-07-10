package com.ecommerce.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 상품 리뷰 (V1.1-1).
 * 1 사용자 = 1 상품 1 리뷰 (uk_review_user_product). 수정으로 갱신한다.
 */
@Entity
@Table(name = "review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@EqualsAndHashCode(of = "id")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // DB는 SMALLINT — 스키마 검증(validate) 정합을 위해 JDBC 타입을 SMALLINT로 매핑
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String content;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 최초 작성 시 null, 수정 시에만 갱신
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    private Review(Long productId, Long userId, int rating, String content) {
        this.productId = productId;
        this.userId    = userId;
        this.rating    = rating;
        this.content   = content;
    }

    /** 리뷰 수정 (본인) — 별점/내용 갱신 */
    public void update(int rating, String content) {
        this.rating    = rating;
        this.content   = content;
        this.updatedAt = LocalDateTime.now();
    }

    /** 작성자 본인 여부 */
    public boolean isOwnedBy(Long userId) {
        return userId != null && userId.equals(this.userId);
    }
}
