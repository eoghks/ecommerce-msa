package com.ecommerce.order.controller;

import com.ecommerce.order.dto.StatsPeriod;
import com.ecommerce.order.dto.response.DailySalesResponse;
import com.ecommerce.order.dto.response.FailedOrderTrendResponse;
import com.ecommerce.order.dto.response.ProductSalesResponse;
import com.ecommerce.order.dto.response.SalesSummaryResponse;
import com.ecommerce.order.dto.response.SellerSalesResponse;
import com.ecommerce.order.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 매출 통계 API (V1.1-7) — ADMIN 전용.
 * 기간(from/to, ISO date, to 포함) 미지정 시 최근 30일. 기간·limit 검증은 이 계층에서 수행한다.
 */
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    /** Top N 기본 개수 */
    private static final int DEFAULT_LIMIT = 10;

    /** Top N 최대 개수 — 초과 요청은 400 */
    private static final int MAX_LIMIT = 50;

    private final StatsService statsService;

    /** 기간 요약 지표 (매출·주문수·AOV·취소율·실패주문) */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/summary")
    public ResponseEntity<SalesSummaryResponse> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(statsService.getSummary(StatsPeriod.of(from, to)));
    }

    /** 일별 매출 추이 (빈 날짜 0 포함, 날짜 오름차순) */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/daily")
    public ResponseEntity<List<DailySalesResponse>> getDailySales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(statsService.getDailySales(StatsPeriod.of(from, to)));
    }

    /** 상품별 매출 Top N (기본 10, 최대 50) */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/products")
    public ResponseEntity<List<ProductSalesResponse>> getTopProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(statsService.getTopProducts(StatsPeriod.of(from, to), resolveLimit(limit)));
    }

    /** 판매자별 매출 Top N (기본 10, 최대 50) */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/sellers")
    public ResponseEntity<List<SellerSalesResponse>> getTopSellers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(statsService.getTopSellers(StatsPeriod.of(from, to), resolveLimit(limit)));
    }

    /** 실패(자동취소) 주문 일별 추이 */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/failed-orders")
    public ResponseEntity<List<FailedOrderTrendResponse>> getFailedOrderTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(statsService.getFailedOrderTrend(StatsPeriod.of(from, to)));
    }

    /** limit 검증 — 미지정 시 기본 10, 1 미만·최대 초과는 400 */
    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "limit 은 1 이상 " + MAX_LIMIT + " 이하여야 합니다.");
        }
        return limit;
    }
}
