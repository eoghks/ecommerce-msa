package com.ecommerce.monitoring.controller;

import com.ecommerce.monitoring.dto.ServiceHealthResponse;
import com.ecommerce.monitoring.service.MonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 모니터링 대시보드 API — ADMIN 전용
 * (게이트웨이가 JWT 검증 후 신뢰 헤더 X-User-Role 주입)
 */
@RestController
@RequestMapping("/api/v1/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private static final String ROLE_ADMIN = "ADMIN";

    private final MonitoringService monitoringService;

    /** 각 서비스 헬스 상태 조회 (ADMIN 전용) */
    @GetMapping("/services")
    public ResponseEntity<List<ServiceHealthResponse>> getServicesHealth(
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        if (!ROLE_ADMIN.equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(monitoringService.collectHealth());
    }
}
