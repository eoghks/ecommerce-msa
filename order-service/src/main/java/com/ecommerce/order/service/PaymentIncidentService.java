package com.ecommerce.order.service;

import com.ecommerce.order.domain.FailedOrderLog;
import com.ecommerce.order.repository.FailedOrderLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 미결 기록 전담 빈 (C-01, H-04).
 * "돈은 움직였을 수 있는데 우리 처리가 끝나지 않은" 건(환불 실패·승인 결과 미확정)을 남긴다.
 *
 * REQUIRES_NEW 로 별도 트랜잭션에서 커밋하는 이유:
 *   환불 실패 예외가 호출부(Saga 보상·반품 승인) 트랜잭션을 롤백시키면 같은 트랜잭션에서 남긴
 *   기록·알림도 함께 사라져 운영자가 미환불 건을 알 수 없다(H-04). 기록만은 반드시 살아남아야 한다.
 * 기록은 관리자 실패주문 조회 API(FailedOrderLog)로 노출되며, 사유에는 금액·상태 같은 추적 정보만 담고
 * 거래키·카드정보는 담지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentIncidentService {

    /** 사유 저장 상한 — DB 컬럼(varchar 300)과 동일 기준 */
    private static final int MAX_REASON_LENGTH = 300;

    private final FailedOrderLogRepository failedOrderLogRepository;

    /**
     * 미결 기록 — 호출부 트랜잭션이 롤백돼도 남는다.
     * 운영 알림은 ERROR 로그(모니터링 수집 대상) + 관리자 실패주문 대장으로 갈음한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long orderId, Long userId, String reason) {
        failedOrderLogRepository.save(FailedOrderLog.builder()
                .orderId(orderId)
                .userId(userId)
                .reason(truncate(reason))
                .build());
        log.error("[결제 미결] 운영 확인 필요. orderId={}, 사유={}", orderId, reason);
    }

    private String truncate(String reason) {
        if (reason == null) {
            return "결제 미결(사유 미기재)";
        }
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH);
    }
}
