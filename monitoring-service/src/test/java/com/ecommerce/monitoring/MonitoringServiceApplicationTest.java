package com.ecommerce.monitoring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스캐폴딩 컴파일 확인 테스트
 * Spring Context 로드 통합 테스트는 Week 6 구현 시 추가
 */
class MonitoringServiceApplicationTest {

    @Test
    @DisplayName("MonitoringServiceApplication 클래스 로드 확인")
    void applicationClassLoads() {
        assertThat(MonitoringServiceApplication.class).isNotNull();
    }
}
