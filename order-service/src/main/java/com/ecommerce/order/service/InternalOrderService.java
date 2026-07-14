package com.ecommerce.order.service;

import com.ecommerce.order.domain.OrderItemStatus;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.exception.InvalidInternalTokenException;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * V1.1-1: 서비스 간 내부 조회 전용 서비스.
 * product-service의 리뷰 작성 구매 인증(X-Internal-Token) 요청을 처리한다.
 */
@Service
@RequiredArgsConstructor
public class InternalOrderService {

    // C1: 실제 재고가 차감된 실구매 상태만 리뷰 자격으로 인정 (미차감 PENDING·전체취소 CANCELLED 제외)
    private static final Set<OrderStatus> PURCHASED_STATUSES =
            Set.of(OrderStatus.CONFIRMED, OrderStatus.PARTIALLY_CANCELLED);

    private final OrderRepository orderRepository;

    // M-N1: auth-service와 공유하는 내부 호출 시크릿. dev 기본값, prod는 INTERNAL_TOKEN env 필수.
    @Value("${app.internal.token:dev-internal-secret}")
    private String internalToken;

    /** M-N1: 내부 호출 공유 시크릿 검증 — 불일치 시 403 */
    public void verifyInternalToken(String token) {
        if (token == null || !internalToken.equals(token)) {
            throw new InvalidInternalTokenException();
        }
    }

    /** 사용자가 상품을 구매(취소되지 않은 ACTIVE 항목 보유)했는지 판정 */
    @Transactional(readOnly = true)
    public boolean isPurchased(Long userId, Long productId) {
        if (userId == null || productId == null) {
            return false;
        }
        return orderRepository.existsPurchasedProduct(
                userId, productId, OrderItemStatus.ACTIVE, PURCHASED_STATUSES);
    }
}
