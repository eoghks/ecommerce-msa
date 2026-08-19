package com.ecommerce.order.service;

import com.ecommerce.order.domain.DeliveryStatus;
import com.ecommerce.order.domain.NotificationType;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.domain.ReturnStatus;
import com.ecommerce.order.dto.AutoConfirmTarget;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 자동 구매확정 건별 처리 (M-1).
 * 배치 전체를 한 트랜잭션으로 묶으면 한 건의 실패가 배치 전체를 롤백시키고,
 * 조회 순서(오래된 배송완료 순)가 고정이라 다음 실행에서도 같은 주문이 선두로 잡혀
 * 자동확정 기능 전체가 영구 정지한다. 건별 독립 트랜잭션으로 실패를 격리한다.
 * 별도 빈으로 둔 이유는 같은 클래스 내 self-invocation 이 프록시를 거치지 않아
 * 트랜잭션 전파 설정이 적용되지 않기 때문이다(OrderPersistenceService 와 동일한 이유).
 */
@Component
@RequiredArgsConstructor
public class AutoConfirmExecutor {

    private final OrderRepository     orderRepository;
    private final NotificationService notificationService;

    /**
     * 조건부 UPDATE 로 1건 확정 — 다른 인스턴스가 선점했거나 자격을 잃었으면 0건이 되어
     * 알림도 발송하지 않는다(H-1·H-2 자격 조건은 UPDATE 문에 함께 들어 있다).
     * @return 실제로 확정됐으면 true
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean confirmOne(AutoConfirmTarget target, LocalDateTime threshold, LocalDateTime confirmedAt) {
        int updated = orderRepository.confirmPurchaseIfEligible(
                target.orderId(), DeliveryStatus.DELIVERED, OrderStatus.confirmableStatuses(),
                ReturnStatus.pendingStatuses(), threshold, confirmedAt);
        if (updated == 0) {
            return false;
        }
        notificationService.create(target.userId(), NotificationType.PURCHASE_CONFIRMED, target.orderId());
        // TODO(V1.1-9 마일리지): 자동확정도 수동확정과 동일하게 적립 훅을 호출한다.
        return true;
    }
}
