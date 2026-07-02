package com.ecommerce.monitoring.service;

import com.ecommerce.monitoring.config.MonitoringProperties;
import com.ecommerce.monitoring.dto.ServiceHealthResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 대상 서비스의 /actuator/health를 수집해 상태를 반환한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitoringService {

    private static final String HEALTH_PATH = "/actuator/health";
    private static final String STATUS_UP = "UP";
    // 응답 DTO 노출용 일반화 사유 (예외 상세는 서버 로그로만 남김)
    private static final String REASON_ABNORMAL = "상태 비정상";
    private static final String REASON_UNREACHABLE = "연결 실패";

    private final RestClient monitoringRestClient;
    private final MonitoringProperties properties;

    /** 전체 대상 서비스의 헬스 상태 수집 — 개별 실패는 DOWN 처리하고 계속 진행 */
    public List<ServiceHealthResponse> collectHealth() {
        return properties.getTargets().stream()
                .map(this::checkOne)
                .toList();
    }

    /** 단일 서비스 헬스 체크 (실패 시 DOWN) */
    private ServiceHealthResponse checkOne(MonitoringProperties.Target target) {
        long start = System.currentTimeMillis();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = monitoringRestClient.get()
                    .uri(target.getUrl() + HEALTH_PATH)
                    .retrieve()
                    .body(Map.class);
            long elapsed = System.currentTimeMillis() - start;

            String status = body == null ? null : String.valueOf(body.get("status"));
            if (STATUS_UP.equals(status)) {
                return ServiceHealthResponse.up(target.getName(), elapsed);
            }
            // 상세 상태값은 서버 로그로만, 응답에는 일반화 사유만 노출
            log.warn("헬스 상태 비정상 - service={}, status={}", target.getName(), status);
            return ServiceHealthResponse.down(target.getName(), elapsed, REASON_ABNORMAL);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            // 예외 원문은 서버 로그로만, 응답에는 일반화 사유만 노출
            log.warn("헬스 체크 실패 - service={}, error={}", target.getName(), e.getMessage());
            return ServiceHealthResponse.down(target.getName(), elapsed, REASON_UNREACHABLE);
        }
    }
}
