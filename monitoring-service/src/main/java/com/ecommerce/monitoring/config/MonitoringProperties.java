package com.ecommerce.monitoring.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 모니터링 대상 서비스 설정 (application.yml: monitoring.targets)
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "monitoring")
public class MonitoringProperties {

    /** 헬스 체크 대상 서비스 목록 */
    private List<Target> targets = new ArrayList<>();

    @Getter
    @Setter
    public static class Target {
        /** 서비스 이름 (예: auth, product) */
        private String name;
        /** 서비스 기본 URL (예: http://localhost:8081) */
        private String url;
    }
}
