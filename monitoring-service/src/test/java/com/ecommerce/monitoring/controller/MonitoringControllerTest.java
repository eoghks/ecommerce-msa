package com.ecommerce.monitoring.controller;

import com.ecommerce.monitoring.dto.ServiceHealthResponse;
import com.ecommerce.monitoring.service.MonitoringService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * MonitoringController 권한/응답 단위 테스트
 */
class MonitoringControllerTest {

    private final MonitoringService monitoringService = mock(MonitoringService.class);
    private final MonitoringController controller = new MonitoringController(monitoringService);

    @Test
    @DisplayName("ADMIN 헤더면 200과 헬스 목록을 반환한다")
    void adminReturnsHealthList() {
        List<ServiceHealthResponse> expected = List.of(
                ServiceHealthResponse.up("auth", 12L),
                ServiceHealthResponse.down("order", 3000L, "Connection refused")
        );
        given(monitoringService.collectHealth()).willReturn(expected);

        var response = controller.getServicesHealth("ADMIN");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    @DisplayName("ADMIN이 아니면 403을 반환한다")
    void nonAdminReturnsForbidden() {
        var response = controller.getServicesHealth("USER");

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("역할 헤더가 없으면 403을 반환한다")
    void missingRoleReturnsForbidden() {
        var response = controller.getServicesHealth(null);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }
}
