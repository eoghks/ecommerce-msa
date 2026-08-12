package com.ecommerce.order.controller;

import com.ecommerce.order.config.SecurityConfig;
import com.ecommerce.order.dto.response.SalesSummaryResponse;
import com.ecommerce.order.service.StatsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통계 API 인가 테스트 (V1.1-7).
 * 게이트웨이가 주입하는 X-User-Role 을 기준으로 @PreAuthorize("hasRole('ADMIN')") 가 동작하는지 검증한다.
 * 토큰이 없는 미인증 요청은 게이트웨이에서 401 로 차단된다(gateway JwtAuthenticationFilterTest 참고).
 */
@WebMvcTest(AdminStatsController.class)
@Import(SecurityConfig.class)
@DisplayName("AdminStatsController 권한 테스트 (V1.1-7)")
class AdminStatsControllerSecurityTest {

    private static final String SUMMARY_URL = "/api/v1/admin/stats/summary";

    @Autowired private MockMvc      mockMvc;
    @MockBean  private StatsService statsService;

    @Test
    @DisplayName("ADMIN 요청 — 200, 날짜는 ISO 문자열로 응답")
    void admin_ok() throws Exception {
        LocalDate from = LocalDate.now().minusDays(29);
        given(statsService.getSummary(any())).willReturn(new SalesSummaryResponse(
                from, LocalDate.now(), 0L, 0L, 0L, 0L, 0L, 0d, 0L));

        mockMvc.perform(get(SUMMARY_URL).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(from.toString()));
    }

    @Test
    @DisplayName("SELLER 요청 — 403 (판매자 통계는 별도 기능)")
    void seller_forbidden() throws Exception {
        mockMvc.perform(get(SUMMARY_URL).header("X-User-Role", "SELLER"))
                .andExpect(status().isForbidden());

        then(statsService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("USER 요청 — 403")
    void user_forbidden() throws Exception {
        mockMvc.perform(get(SUMMARY_URL).header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());

        then(statsService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("역할 헤더 없는 요청 — 403 (L-3: 실제 상태코드 단정)")
    void noRole_forbidden() throws Exception {
        mockMvc.perform(get(SUMMARY_URL))
                .andExpect(status().isForbidden());

        then(statsService).shouldHaveNoInteractions();
    }
}
