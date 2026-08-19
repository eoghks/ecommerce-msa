package com.ecommerce.order.service;

import com.ecommerce.order.support.ServiceTimeZone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 자동 구매확정 스케줄러 (payment-foundation §3.2).
 * 매시 정각에 배송완료 후 기준일(기본 7일)이 지난 미확정 주문을 확정한다.
 * 다중 인스턴스 중복 확정은 서비스의 조건부 UPDATE 로 차단되므로 분산 락을 두지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseConfirmScheduler {

    /** 매시 정각 실행 — 확정 시점이 최대 1시간 늦어져도 사용자 영향이 없다(반품 가능 기간이 늘 뿐) */
    private static final String HOURLY_CRON = "0 0 * * * *";

    private final PurchaseConfirmService purchaseConfirmService;

    @Scheduled(cron = HOURLY_CRON, zone = ServiceTimeZone.ZONE_ID)
    public void autoConfirm() {
        int confirmed = purchaseConfirmService.autoConfirmExpired();
        if (confirmed > 0) {
            log.info("자동 구매확정 스케줄러 실행. 확정 건수={}", confirmed);
        }
    }
}
