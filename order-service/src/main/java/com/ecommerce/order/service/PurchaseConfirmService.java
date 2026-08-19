package com.ecommerce.order.service;

import com.ecommerce.order.domain.DeliveryStatus;
import com.ecommerce.order.domain.NotificationType;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.dto.AutoConfirmTarget;
import com.ecommerce.order.dto.response.OrderResponse;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.exception.UnauthorizedException;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 구매확정 서비스 (payment-foundation §3.2).
 * - 수동 확정: 배송완료(DELIVERED) + 미확정 주문을 소유자 본인이 확정. 확정 시 반품 자격이 사라진다.
 * - 자동 확정: 배송완료 후 기준일(기본 7일) 경과한 미확정 주문을 스케줄러가 일괄 확정.
 *   다중 인스턴스 중복 확정은 조건부 UPDATE(confirmPurchaseIfEligible)로 원자 차단한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseConfirmService {

    private final OrderRepository     orderRepository;
    private final NotificationService notificationService;

    /** 배송완료 후 자동확정까지의 대기 일수(정책값). 정책 테이블 도입은 마일리지 단계(V1.1-9) */
    @Value("${app.order.auto-confirm-days:7}")
    private int autoConfirmDays;

    /** 1회 실행당 자동확정 처리 상한 — 대량 적체 시에도 트랜잭션이 길어지지 않게 제한한다 */
    @Value("${app.order.auto-confirm-batch-size:500}")
    private int autoConfirmBatchSize;

    /**
     * 수동 구매확정 — 주문 소유자 본인만.
     * 자격 검증(배송완료·미확정)은 도메인(Order.confirmPurchase)이 담당한다.
     * 타인 주문은 404(정보 노출 방지), 인증 정보 부재는 401.
     */
    @Transactional
    public OrderResponse confirm(Long orderId, Long userId) {
        requireUser(userId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.getUserId().equals(userId)) {
            throw new OrderNotFoundException(orderId);
        }

        order.confirmPurchase(LocalDateTime.now());
        notificationService.create(order.getUserId(), NotificationType.PURCHASE_CONFIRMED, orderId);
        // TODO(V1.1-9 마일리지): 이 지점에서 payableAmount 기준 적립을 호출한다(§3.1 적립 기준 = 실결제액).
        log.info("구매확정. orderId={}, userId={}", orderId, userId);
        return OrderResponse.from(order);
    }

    /**
     * 자동 구매확정 — 배송완료 후 기준일이 지난 미확정 주문 일괄 확정.
     * 조회한 대상마다 조건부 UPDATE 를 수행해, 갱신에 성공한(1건) 주문만 알림을 발송한다.
     * @return 실제로 확정된 주문 수
     */
    @Transactional
    public int autoConfirmExpired() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusDays(autoConfirmDays);
        List<AutoConfirmTarget> targets = orderRepository.findAutoConfirmTargets(
                DeliveryStatus.DELIVERED, threshold, PageRequest.of(0, autoConfirmBatchSize));

        int confirmed = 0;
        for (AutoConfirmTarget target : targets) {
            if (confirmOne(target, threshold, now)) {
                confirmed++;
            }
        }
        log.info("자동 구매확정 완료. 대상={}, 확정={}, 기준일수={}",
                targets.size(), confirmed, autoConfirmDays);
        return confirmed;
    }

    /** 조건부 UPDATE 로 1건 확정 — 다른 인스턴스가 선점했으면 0건이 되어 알림도 발송하지 않는다 */
    private boolean confirmOne(AutoConfirmTarget target, LocalDateTime threshold, LocalDateTime now) {
        int updated = orderRepository.confirmPurchaseIfEligible(
                target.orderId(), DeliveryStatus.DELIVERED, threshold, now);
        if (updated == 0) {
            return false;
        }
        notificationService.create(target.userId(), NotificationType.PURCHASE_CONFIRMED, target.orderId());
        // TODO(V1.1-9 마일리지): 자동확정도 수동확정과 동일하게 적립 훅을 호출한다.
        return true;
    }

    /** userId 부재 → 401 */
    private void requireUser(Long userId) {
        if (userId == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
    }
}
