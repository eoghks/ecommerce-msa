package com.ecommerce.order.controller;

import com.ecommerce.order.dto.StatsPeriod;
import com.ecommerce.order.dto.response.DailySalesResponse;
import com.ecommerce.order.dto.response.FailedOrderTrendResponse;
import com.ecommerce.order.dto.response.ProductSalesResponse;
import com.ecommerce.order.dto.response.SalesSummaryResponse;
import com.ecommerce.order.dto.response.SellerSalesResponse;
import com.ecommerce.order.exception.OrderExceptionHandler;
import com.ecommerce.order.service.StatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminStatsController 통계 API 단위 테스트 (V1.1-7)")
class AdminStatsControllerTest {

    @InjectMocks private AdminStatsController adminStatsController;
    @Mock        private StatsService         statsService;

    private MockMvc mockMvc;

    private static final LocalDate FROM = LocalDate.of(2026, 7, 1);
    private static final LocalDate TO   = LocalDate.of(2026, 7, 31);

    @BeforeEach
    void setUp() {
        // 날짜(LocalDate)를 애플리케이션과 동일하게 ISO 문자열로 직렬화하도록 컨버터를 지정
        mockMvc = MockMvcBuilders.standaloneSetup(adminStatsController)
                .setControllerAdvice(new OrderExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        Jackson2ObjectMapperBuilder.json()
                                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                                .build()))
                .build();
    }

    // ── 요약 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /summary — 200 + 요약 지표 응답")
    void getSummary_ok() throws Exception {
        given(statsService.getSummary(any())).willReturn(new SalesSummaryResponse(
                FROM, TO, 12_450_000L, 87L, 143_103L, 5L, 3L, 0.0842d, 2L));

        mockMvc.perform(get("/api/v1/admin/stats/summary")
                        .param("from", "2026-07-01")
                        .param("to", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(12_450_000L))
                .andExpect(jsonPath("$.orderCount").value(87))
                .andExpect(jsonPath("$.averageOrderValue").value(143_103L))
                .andExpect(jsonPath("$.fullyCancelledCount").value(5))
                .andExpect(jsonPath("$.partiallyCancelledCount").value(3))
                .andExpect(jsonPath("$.cancelRate").value(0.0842))
                .andExpect(jsonPath("$.failedOrderCount").value(2));
    }

    @Test
    @DisplayName("GET /summary — 기간 미지정 시 최근 30일로 조회")
    void getSummary_noPeriod_usesLast30Days() throws Exception {
        given(statsService.getSummary(any())).willReturn(emptySummary());

        mockMvc.perform(get("/api/v1/admin/stats/summary"))
                .andExpect(status().isOk());

        StatsPeriod period = capturedSummaryPeriod();
        assertThat(period.to()).isEqualTo(LocalDate.now());
        assertThat(period.from()).isEqualTo(LocalDate.now().minusDays(29));
    }

    @Test
    @DisplayName("GET /summary — from > to 이면 400")
    void getSummary_fromAfterTo_badRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats/summary")
                        .param("from", "2026-07-02")
                        .param("to", "2026-07-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("조회 시작일이 종료일보다 늦을 수 없습니다."));

        then(statsService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("GET /summary — 366일 초과 조회는 400")
    void getSummary_periodTooLong_badRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats/summary")
                        .param("from", "2025-01-01")
                        .param("to", "2026-07-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("조회 기간은 최대 366일까지 가능합니다."));

        then(statsService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("GET /summary — 366일 경계는 200")
    void getSummary_maxPeriod_ok() throws Exception {
        given(statsService.getSummary(any())).willReturn(emptySummary());

        mockMvc.perform(get("/api/v1/admin/stats/summary")
                        .param("from", "2025-07-31")
                        .param("to", "2026-07-31"))
                .andExpect(status().isOk());
    }

    // ── 일별 추이 ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /daily — 200 + 일별 추이 배열")
    void getDailySales_ok() throws Exception {
        given(statsService.getDailySales(any())).willReturn(List.of(
                new DailySalesResponse(FROM, 1_000L, 1L),
                new DailySalesResponse(FROM.plusDays(1), 0L, 0L)));

        mockMvc.perform(get("/api/v1/admin/stats/daily")
                        .param("from", "2026-07-01")
                        .param("to", "2026-07-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2026-07-01"))
                .andExpect(jsonPath("$[1].revenue").value(0));
    }

    // ── Top N ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /products — limit 미지정 시 기본 10")
    void getTopProducts_defaultLimit() throws Exception {
        given(statsService.getTopProducts(any(), anyInt()))
                .willReturn(List.of(new ProductSalesResponse(1L, "상품A", 5_000L, 5L)));

        mockMvc.perform(get("/api/v1/admin/stats/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productName").value("상품A"));

        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        then(statsService).should().getTopProducts(any(), limit.capture());
        assertThat(limit.getValue()).isEqualTo(10);
    }

    @Test
    @DisplayName("GET /products — limit 50은 허용, 51은 400")
    void getTopProducts_limitBoundary() throws Exception {
        given(statsService.getTopProducts(any(), anyInt())).willReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/stats/products").param("limit", "50"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/stats/products").param("limit", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("limit 은 1 이상 50 이하여야 합니다."));
    }

    @Test
    @DisplayName("GET /products — limit 0 이하는 400")
    void getTopProducts_limitZero_badRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats/products").param("limit", "0"))
                .andExpect(status().isBadRequest());

        then(statsService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("GET /sellers — 200 + 판매자별 매출 (sellerId null 은 플랫폼 상품)")
    void getTopSellers_ok() throws Exception {
        given(statsService.getTopSellers(any(), anyInt())).willReturn(List.of(
                new SellerSalesResponse(7L, 9_000L, 3L, 2L),
                new SellerSalesResponse(null, 1_000L, 1L, 1L)));

        mockMvc.perform(get("/api/v1/admin/stats/sellers").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sellerId").value(7))
                .andExpect(jsonPath("$[1].sellerId").doesNotExist());
    }

    // ── 실패주문 추이 ────────────────────────────────────────────

    @Test
    @DisplayName("GET /failed-orders — 200 + 일별 실패 건수")
    void getFailedOrderTrend_ok() throws Exception {
        given(statsService.getFailedOrderTrend(any()))
                .willReturn(List.of(new FailedOrderTrendResponse(FROM, 2L)));

        mockMvc.perform(get("/api/v1/admin/stats/failed-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-07-01"))
                .andExpect(jsonPath("$[0].count").value(2));
    }

    // ── helpers ─────────────────────────────────────────────────

    private SalesSummaryResponse emptySummary() {
        return new SalesSummaryResponse(FROM, TO, 0L, 0L, 0L, 0L, 0L, 0d, 0L);
    }

    private StatsPeriod capturedSummaryPeriod() {
        ArgumentCaptor<StatsPeriod> captor = ArgumentCaptor.forClass(StatsPeriod.class);
        then(statsService).should().getSummary(captor.capture());
        return captor.getValue();
    }
}
