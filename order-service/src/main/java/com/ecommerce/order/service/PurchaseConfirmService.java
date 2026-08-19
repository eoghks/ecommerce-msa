package com.ecommerce.order.service;

import com.ecommerce.order.domain.DeliveryStatus;
import com.ecommerce.order.domain.NotificationType;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.domain.ReturnStatus;
import com.ecommerce.order.dto.AutoConfirmTarget;
import com.ecommerce.order.dto.response.OrderResponse;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.exception.PurchaseAlreadyConfirmedException;
import com.ecommerce.order.exception.PurchaseConfirmNotAllowedException;
import com.ecommerce.order.exception.UnauthorizedException;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.ReturnRequestRepository;
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
 * - 수동·자동 모두 조건부 UPDATE 로 확정하므로(H-3) 동시 요청·다중 인스턴스에서도 확정과 알림은 1회뿐이다.
 * - 확정 자격은 주문상태(H-1)와 진행 중 반품 유무(H-2)까지 함께 본다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseConfirmService {

    /** 자동확정 건별 처리 결과 — 확정 / 선점·자격상실로 건너뜀 / 실패(격리) */
    private enum AutoConfirmOutcome { CONFIRMED, SKIPPED, FAILED }

    private final OrderRepository         orderRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final NotificationService     notificationService;
    private final AutoConfirmExecutor     autoConfirmExecutor;

    /** 배송완료 후 자동확정까지의 대기 일수(정책값). 정책 테이블 도입은 마일리지 단계(V1.1-9) */
    @Value("${app.order.auto-confirm-days:7}")
    private int autoConfirmDays;

    /** 1회 실행당 자동확정 처리 상한 — 대량 적체 시에도 배치가 길어지지 않게 제한한다 */
    @Value("${app.order.auto-confirm-batch-size:500}")
    private int autoConfirmBatchSize;

    /**
     * 수동 구매확정 — 주문 소유자 본인만.
     * 자격 판정(주문상태·배송완료·미확정)은 도메인(Order.validateConfirmable)이, 진행 중 반품 검증은
     * 서비스가 담당하고, 실제 기록은 조건부 UPDATE 로 원자 처리한다.
     * 타인 주문은 404(정보 노출 방지), 인증 정보 부재는 401, 경합에서 밀리면 409.
     */
    @Transactional
    public OrderResponse confirm(Long orderId, Long userId) {
        requireUser(userId);
        Order order = findOwnedOrder(orderId, userId);
        order.validateConfirmable();
        requireNoPendingReturn(orderId);

        LocalDateTime now = LocalDateTime.now();
        int updated = orderRepository.confirmPurchaseNow(orderId, DeliveryStatus.DELIVERED,
                OrderStatus.confirmableStatuses(), ReturnStatus.pendingStatuses(), now);
        if (updated == 0) {
            // 검증 이후 자동확정·다른 요청이 선점한 경우 — 확정도 알림도 중복 발생시키지 않는다
            throw new PurchaseAlreadyConfirmedException(orderId);
        }

        notificationService.create(userId, NotificationType.PURCHASE_CONFIRMED, orderId);
        // TODO(V1.1-9 마일리지): 이 지점에서 payableAmount 기준 적립을 호출한다(§3.1 적립 기준 = 실결제액).
        log.info("구매확정. orderId={}, userId={}", orderId, userId);
        // 조건부 UPDATE 는 영속성 컨텍스트를 비우므로(clearAutomatically) 확정 결과를 다시 읽어 응답한다
        return OrderResponse.from(findOrder(orderId));
    }

    /**
     * 자동 구매확정 — 배송완료 후 기준일이 지난 미확정 주문 일괄 확정.
     * 건별 독립 트랜잭션(AutoConfirmExecutor)으로 처리해 한 건의 실패가 배치 전체를 멈추지 않게 한다(M-1).
     * @return 실제로 확정된 주문 수
     */
    public int autoConfirmExpired() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusDays(autoConfirmDays);
        List<AutoConfirmTarget> targets = orderRepository.findAutoConfirmTargets(
                DeliveryStatus.DELIVERED, OrderStatus.confirmableStatuses(),
                ReturnStatus.pendingStatuses(), threshold, PageRequest.of(0, autoConfirmBatchSize));

        int confirmed = 0;
        int failed = 0;
        for (AutoConfirmTarget target : targets) {
            AutoConfirmOutcome outcome = confirmOneIsolated(target, threshold, now);
            confirmed += (outcome == AutoConfirmOutcome.CONFIRMED) ? 1 : 0;
            failed    += (outcome == AutoConfirmOutcome.FAILED) ? 1 : 0;
        }
        log.info("자동 구매확정 완료. 대상={}, 확정={}, 실패={}, 기준일수={}",
                targets.size(), confirmed, failed, autoConfirmDays);
        return confirmed;
    }

    /**
     * 1건 확정을 독립 트랜잭션으로 실행하고 실패를 격리한다(M-1).
     * 실패 건은 확정되지 않은 채 남으므로 다음 실행에서 다시 대상이 된다.
     */
    private AutoConfirmOutcome confirmOneIsolated(AutoConfirmTarget target,
                                                  LocalDateTime threshold, LocalDateTime now) {
        try {
            return autoConfirmExecutor.confirmOne(target, threshold, now)
                    ? AutoConfirmOutcome.CONFIRMED
                    : AutoConfirmOutcome.SKIPPED;
        } catch (RuntimeException e) {
            log.error("자동 구매확정 실패 — 해당 건만 건너뛴다. orderId={}", target.orderId(), e);
            return AutoConfirmOutcome.FAILED;
        }
    }

    /** H-2: 진행 중(REQUESTED/APPROVED) 반품이 있으면 확정 불가 — 400 */
    private void requireNoPendingReturn(Long orderId) {
        if (returnRequestRepository.existsByOrderIdAndStatusIn(orderId, ReturnStatus.pendingStatuses())) {
            throw new PurchaseConfirmNotAllowedException(
                    "처리 중인 반품이 있는 주문은 구매확정할 수 없습니다. 반품 처리 완료 후 확정해주세요.");
        }
    }

    /** 본인 소유 주문 조회 — 타인 주문은 404 (정보 노출 방지) */
    private Order findOwnedOrder(Long orderId, Long userId) {
        Order order = findOrder(orderId);
        if (!order.getUserId().equals(userId)) {
            throw new OrderNotFoundException(orderId);
        }
        return order;
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /** userId 부재 → 401 */
    private void requireUser(Long userId) {
        if (userId == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
    }
}
