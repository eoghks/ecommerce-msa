package com.ecommerce.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스캐폴딩 컴파일 확인 테스트
 * Spring Context 로드 (DB·Redis·Kafka 통합) 는 Week 2 구현 시 Testcontainers 로 추가
 */
class AuthServiceApplicationTest {

    @Test
    @DisplayName("AuthServiceApplication 클래스 로드 확인")
    void applicationClassLoads() {
        assertThat(AuthServiceApplication.class).isNotNull();
    }
}
