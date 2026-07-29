package com.ecommerce.order.service;

import com.ecommerce.order.domain.DeliveryStatus;
import com.ecommerce.order.domain.NotificationType;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.ReturnRequest;
import com.ecommerce.order.domain.ReturnStatus;
import com.ecommerce.order.dto.response.ReturnResponse;
import com.ecommerce.order.event.OrderItemCancelledApplicationEvent;
import com.ecommerce.order.event.OrderItemCancelledEvent;
import com.ecommerce.order.exception.DuplicateReturnRequestException;
import com.ecommerce.order.exception.OrderItemNotFoundException;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.exception.ReturnAccessDeniedException;
import com.ecommerce.order.exception.ReturnNotAllowedException;
import com.ecommerce.order.exception.ReturnRequestNotFoundException;
import com.ecommerce.order.exception.UnauthorizedException;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.ReturnRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 반품·환불 서비스 (V1.1-5).
 * - 신청: 배송완료(DELIVERED) 주문의 활성 항목, 주문 소유자 본인만. 중복 진행 시 409.
 * - 승인: 기존 항목취소 경로(cancelItem + OrderItemCancelledEvent)를 재사용해 재고 복구 후
 *         환불 훅(processRefund) 호출 → REFUNDED 전이.
 * - 거부: 거부 사유 필수. 거부된 항목은 재신청 허용(DB 부분 유니크가 REJECTED 제외).
 * - 권한: ADMIN 전체 / SELLER 는 반품 대상 항목이 본인 상품인 경우만. 그 외 403.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnService {

    /** 권한 판정용 역할 코드 */
    private static final String ROLE_ADMIN  = "ADMIN";
    private static final String ROLE_SELLER = "SELLER";

    /** 반품 승인으로 항목을 취소할 때 남기는 사유 */
    private static final String RETURN_CANCEL_REASON = "반품 승인";

    /** 반품 사유 최대 길이 — 요청 DTO(@Size)와 동일 기준 사용 */
    private static final int MAX_REASON_LENGTH = ReturnRequest.MAX_REASON_LENGTH;

    private final ReturnRequestRepository   returnRequestRepository;
    private final OrderRepository           orderRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final NotificationService       notificationService;

    /**
     * 반품 신청 — 주문 소유자 본인만.
     * 자격: 배송완료(DELIVERED) 주문 + 대상 항목 ACTIVE. 미충족 시 400.
     * 동일 항목에 진행 중(REQUESTED/APPROVED/REFUNDED)인 반품이 있으면 409.
     */
    @Transactional
    public ReturnResponse request(Long orderId, Long itemId, Long userId, String reason) {
        requireUser(userId);
        validateReason(reason);

        Order order = findOwnedOrder(orderId, userId);
        OrderItem item = findItem(order, itemId);
        validateEligible(order, item);

        if (returnRequestRepository.existsByOrderItemIdAndStatusIn(itemId, ReturnStatus.activeStatuses())) {
            throw new DuplicateReturnRequestException(itemId);
        }

        ReturnRequest saved = returnRequestRepository.save(ReturnRequest.builder()
                .orderId(orderId)
                .orderItemId(itemId)
                .userId(userId)
                .reason(reason)
                .build());
        log.info("반품 신청 접수. returnId={}, orderId={}, itemId={}", saved.getId(), orderId, itemId);
        return ReturnResponse.from(saved);
    }

    /**
     * 반품 승인 (ADMIN / 대상 항목의 SELLER).
     * REQUESTED 에서만 가능(그 외 400). 대상 항목을 취소 처리해 기존 재고복구 Saga를 재사용하고,
     * 환불 훅(processRefund) 처리 후 REFUNDED 로 전이한다.
     * M-2: 신청 이후 다른 경로로 취소된 항목은 이중 환불이 되므로 승인 시점에 다시 검증한다.
     */
    @Transactional
    public ReturnResponse approve(Long returnId, Long userId, String role) {
        requireUser(userId);
        ReturnRequest returnRequest = findReturn(returnId);
        Order order = findOrder(returnRequest.getOrderId());
        OrderItem item = findItem(order, returnRequest.getOrderItemId());
        requireManagePermission(item, returnId, userId, role);

        LocalDateTime now = LocalDateTime.now();
        returnRequest.approve(now);   // REQUESTED 아니면 400
        requireActiveItem(item);      // M-2: 이미 취소된 항목이면 400 → 트랜잭션 롤백으로 승인 취소
        restockApprovedItem(order, item.getId());
        notificationService.create(returnRequest.getUserId(),
                NotificationType.RETURN_APPROVED, returnRequest.getOrderId());

        processRefund(returnRequest);
        returnRequest.markRefunded(now);
        notificationService.create(returnRequest.getUserId(),
                NotificationType.RETURN_REFUNDED, returnRequest.getOrderId());

        log.info("반품 승인·환불 완료. returnId={}, orderId={}, by={}({})",
                returnId, returnRequest.getOrderId(), userId, role);
        return ReturnResponse.from(returnRequest);
    }

    /**
     * 반품 거부 (ADMIN / 대상 항목의 SELLER). REQUESTED 에서만 가능, 거부 사유 필수.
     * 거부된 항목은 재신청 허용.
     */
    @Transactional
    public ReturnResponse reject(Long returnId, Long userId, String role, String rejectReason) {
        requireUser(userId);
        validateRejectReason(rejectReason);
        ReturnRequest returnRequest = findReturn(returnId);
        Order order = findOrder(returnRequest.getOrderId());
        requireManagePermission(findItem(order, returnRequest.getOrderItemId()),
                returnId, userId, role);

        returnRequest.reject(rejectReason, LocalDateTime.now());
        notificationService.create(returnRequest.getUserId(),
                NotificationType.RETURN_REJECTED, returnRequest.getOrderId());

        log.info("반품 거부. returnId={}, orderId={}, by={}({})",
                returnId, returnRequest.getOrderId(), userId, role);
        return ReturnResponse.from(returnRequest);
    }

    /** 내 반품 목록 (최신순 페이징) — 본인 것만 */
    @Transactional(readOnly = true)
    public Page<ReturnResponse> getMyReturns(Long userId, Pageable pageable) {
        requireUser(userId);
        return returnRequestRepository.findByUserIdOrderByRequestedAtDesc(userId, pageable)
                .map(ReturnResponse::from);
    }

    /** 반품 관리 목록 — ADMIN 전체 / SELLER 는 본인 상품 항목이 대상인 건만(M-3). 그 외 403 */
    @Transactional(readOnly = true)
    public Page<ReturnResponse> getManagedReturns(Long userId, String role, Pageable pageable) {
        requireUser(userId);
        if (ROLE_ADMIN.equals(role)) {
            return returnRequestRepository.findAllByOrderByRequestedAtDesc(pageable)
                    .map(ReturnResponse::from);
        }
        if (ROLE_SELLER.equals(role)) {
            return returnRequestRepository.findBySellerId(userId, pageable)
                    .map(ReturnResponse::from);
        }
        throw new ReturnAccessDeniedException("반품을 조회할 권한이 없습니다.");
    }

    /**
     * 환불 처리 훅 — 현재 결제가 mock 이므로 성공 처리 + 로그만 남긴다.
     * V1.1-6(PG 연동) 교체 지점: 이 메서드에서 실제 결제취소(PG 환불) API를 호출하고,
     * 응답에 따라 APPROVED(환불요청)→REFUNDED 전이를 비동기로 분리한다.
     */
    private void processRefund(ReturnRequest returnRequest) {
        log.info("환불 처리(mock 성공). returnId={}, orderId={}, itemId={}",
                returnRequest.getId(), returnRequest.getOrderId(), returnRequest.getOrderItemId());
    }

    /**
     * 승인된 항목 취소 처리 — 기존 항목취소 재고복구 경로 재사용.
     * 실제 ACTIVE→CANCELLED 전이가 일어난 경우에만 복구 이벤트를 발행(멱등키 동일 → 중복 복구 방지)하고,
     * 주문 상태·합계는 도메인의 취소 후 재계산 로직을 따른다.
     */
    private void restockApprovedItem(Order order, Long itemId) {
        order.cancelItem(itemId, RETURN_CANCEL_REASON).ifPresent(cancelled ->
                applicationEventPublisher.publishEvent(new OrderItemCancelledApplicationEvent(
                        new OrderItemCancelledEvent(order.getId(), cancelled.getId(),
                                cancelled.getProductId(), cancelled.getQuantity()))));
    }

    private ReturnRequest findReturn(Long returnId) {
        return returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new ReturnRequestNotFoundException(returnId));
    }

    /** 반품 처리 권한 검증 — 권한 없으면 403 */
    private void requireManagePermission(OrderItem item, Long returnId, Long userId, String role) {
        if (!canManage(item, userId, role)) {
            throw new ReturnAccessDeniedException("반품을 처리할 권한이 없습니다. id=" + returnId);
        }
    }

    /**
     * 반품 처리 권한 판정 — 반품은 항목 단위 연산이므로 항목 취소(OrderService.cancelOrderItem)와
     * 동일하게 "대상 항목의 소유 판매자"만 허용한다(H-1). ADMIN 은 전체 허용.
     */
    private boolean canManage(OrderItem item, Long userId, String role) {
        if (ROLE_ADMIN.equals(role)) {
            return true;
        }
        return ROLE_SELLER.equals(role) && item.isOwnedBy(userId);
    }

    /** M-2: 승인 시점 항목 재검증 — 이미 취소된 항목이면 400 (이중 환불 차단) */
    private void requireActiveItem(OrderItem item) {
        if (!item.isActive()) {
            throw new ReturnNotAllowedException(
                    "이미 취소된 항목은 반품을 승인할 수 없습니다. itemId=" + item.getId());
        }
    }

    /** 반품 자격 검증 — 배송완료 주문 + 활성 항목만. 미충족 시 400 */
    private void validateEligible(Order order, OrderItem item) {
        if (order.getDeliveryStatus() != DeliveryStatus.DELIVERED) {
            throw new ReturnNotAllowedException(
                    "배송 완료된 주문만 반품할 수 있습니다. 현재 배송상태: " + order.getDeliveryStatus());
        }
        if (!item.isActive()) {
            throw new ReturnNotAllowedException(
                    "이미 취소된 항목은 반품할 수 없습니다. itemId=" + item.getId());
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

    private OrderItem findItem(Order order, Long itemId) {
        return order.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new OrderItemNotFoundException(itemId));
    }

    private void validateReason(String reason) {
        if (isBlank(reason)) {
            throw new ReturnNotAllowedException("반품 사유를 입력해주세요.");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new ReturnNotAllowedException(
                    "반품 사유는 " + MAX_REASON_LENGTH + "자 이하여야 합니다.");
        }
    }

    private void validateRejectReason(String rejectReason) {
        if (isBlank(rejectReason)) {
            throw new ReturnNotAllowedException("거부 사유를 입력해주세요.");
        }
        if (rejectReason.length() > MAX_REASON_LENGTH) {
            throw new ReturnNotAllowedException(
                    "거부 사유는 " + MAX_REASON_LENGTH + "자 이하여야 합니다.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** userId 부재 → 401 */
    private void requireUser(Long userId) {
        if (userId == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
    }
}
