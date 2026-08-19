package com.ecommerce.order.dto.response;

import com.ecommerce.order.domain.DeliveryStatus;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 판매자 주문 목록 응답 (M-6).
 * 해당 판매자의 항목만 노출하고, 금액도 그 항목들의 정가 합계(sellerItemsTotal)만 담는다.
 *
 * 구매자 응답(OrderResponse)과 분리한 이유: 판매자 뷰의 금액은 "본인 항목 정가 합계"일 뿐
 * 주문의 실결제액(payableAmount)이 아니다. 같은 record 를 재사용하면 쿠폰·마일리지 도입 후
 * 정산(V1.2-7)이 판매자별 정가 합계를 실결제액으로 오인해 집계할 위험이 있다.
 * 판매자별 할인 배분(§4.2)이 정의되면 이 record 에 배분 결과 필드를 추가한다.
 */
public record SellerOrderResponse(
        Long id,
        Long userId,
        OrderStatus status,
        DeliveryStatus deliveryStatus,
        Long sellerItemsTotal,
        LocalDateTime deliveredAt,
        LocalDateTime purchaseConfirmedAt,
        List<OrderItemResponse> items,
        String receiver,
        String phone,
        String address,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SellerOrderResponse from(Order order, Long sellerId) {
        List<OrderItemResponse> myItems = order.getItems().stream()
                .filter(item -> item.isOwnedBy(sellerId))
                .map(OrderItemResponse::from)
                .toList();
        long sellerItemsTotal = myItems.stream().mapToLong(OrderItemResponse::subtotal).sum();
        return new SellerOrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getDeliveryStatus(),
                sellerItemsTotal,
                order.getDeliveredAt(),
                order.getPurchaseConfirmedAt(),
                myItems,
                order.getReceiver(),
                order.getPhone(),
                order.getAddress(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
