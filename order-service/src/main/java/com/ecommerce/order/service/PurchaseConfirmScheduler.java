package com.ecommerce.order.service;

import com.ecommerce.order.support.ServiceTimeZone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주문 라이프사이클 스케줄러 (payment-foundation §3.2, §11.1).
 * - 자동 구매확정: 매시 정각에 배송완료 후 기준일(기본 7일)이 지난 미확정 주문을 확정한다.
 * - 결제 미완료 만료: 결제 대기(PAYMENT_PENDING)로 방치된 주문을 정책 시간(기본 30분) 후 취소한다.
 * - 진행 중 결제 회수(C-01/C-02): 승인 왕복 중 프로세스가 끊겨 남은 결제를 PG 재조회로 확정한다.
 * 두 작업 모두 조건부 UPDATE 로 원자 처리되므로 다중 인스턴스에서도 분산 락이 필요 없다.
 * 만료는 §11.1 에 따라 스케줄러를 새로 만들지 않고 이 스케줄러에 함께 태운다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseConfirmScheduler {

    /** 매시 정각 실행 — 확정 시점이 최대 1시간 늦어져도 사용자 영향이 없다(반품 가능 기간이 늘 뿐) */
    private static final String HOURLY_CRON = "0 0 * * * *";

    /**
     * 10분 주기 실행 — 만료 기준(기본 30분)보다 짧게 잡아야 만료가 제때 반영된다.
     * 매시 정각(자동확정)과 같은 주기를 쓰면 최대 1시간 늦어져 결제 대기 주문이 오래 남는다.
     */
    private static final String EVERY_TEN_MINUTES_CRON = "0 */10 * * * *";

    /**
     * C-01/C-02: 진행 중·미확정 결제 회수 주기 — 만료(10분)보다 자주 돌려
     * 고아 결제가 주문 만료·재결제를 오래 막지 않게 한다.
     */
    private static final String EVERY_FIVE_MINUTES_CRON = "0 */5 * * * *";

    private final PurchaseConfirmService  purchaseConfirmService;
    private final PaymentService          paymentService;
    private final PaymentRecoveryService  paymentRecoveryService;

    @Scheduled(cron = HOURLY_CRON, zone = ServiceTimeZone.ZONE_ID)
    public void autoConfirm() {
        int confirmed = purchaseConfirmService.autoConfirmExpired();
        if (confirmed > 0) {
            log.info("자동 구매확정 스케줄러 실행. 확정 건수={}", confirmed);
        }
    }

    /** V1.1-6: 결제 대기로 방치된 주문 만료 처리 (§11.1) */
    @Scheduled(cron = EVERY_TEN_MINUTES_CRON, zone = ServiceTimeZone.ZONE_ID)
    public void expireUnpaidOrders() {
        int expired = paymentService.expireUnpaidOrders();
        if (expired > 0) {
            log.info("결제 미완료 주문 만료 스케줄러 실행. 만료 건수={}", expired);
        }
    }

    /**
     * C-01/C-02: 진행 중·미확정 결제 회수 — PG 재조회로 승인 여부를 확정한다.
     * 승인이 확인되면 정상 확정하고, 미승인이면 결제만 닫아 재결제를 허용한다.
     */
    @Scheduled(cron = EVERY_FIVE_MINUTES_CRON, zone = ServiceTimeZone.ZONE_ID)
    public void reconcileInFlightPayments() {
        int resolved = paymentRecoveryService.reconcileInFlightPayments();
        if (resolved > 0) {
            log.info("진행 중 결제 회수 스케줄러 실행. 확정 건수={}", resolved);
        }
    }
}
