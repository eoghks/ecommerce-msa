package com.ecommerce.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 임시 비밀번호 발급 후 강제 변경 여부
    @Column(nullable = false)
    private boolean passwordChangeRequired = false;

    @Builder
    private User(String email, String password, String name, Role role) {
        this.email    = email;
        this.password = password;
        this.name     = name;
        this.role     = role;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.passwordChangeRequired = false;
    }

    // 임시 비밀번호 적용 — 다음 로그인 시 비밀번호 변경 강제
    public void applyTempPassword(String encodedPassword) {
        this.password = encodedPassword;
        this.passwordChangeRequired = true;
    }
}
