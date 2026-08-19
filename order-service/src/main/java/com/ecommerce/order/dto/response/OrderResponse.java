package com.ecommerce.order.dto.response;

import com.ecommerce.order.domain.DeliveryStatus;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        Long userId,
        OrderStatus status,
        DeliveryStatus deliveryStatus,
        Long totalPrice,
        Long itemsTotal,
        Long couponDiscount,
        Long mileageUsed,
        Long payableAmount,
        Long mileageEarned,
        LocalDateTime deliveredAt,
        LocalDateTime purchaseConfirmedAt,
        List<OrderItemResponse> items,
        String receiver,
        String phone,
        String address,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getDeliveryStatus(),
                order.getTotalPrice(),
                order.getItemsTotal(),
                order.getCouponDiscount(),
                order.getMileageUsed(),
                order.getPayableAmount(),
                order.getMileageEarned(),
                order.getDeliveredAt(),
                order.getPurchaseConfirmedAt(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getReceiver(),
                order.getPhone(),
                order.getAddress(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    /**
     * 판매자 화면용 — 해당 판매자의 항목만 노출하고, 합계도 그 항목만으로 재계산.
     * 한 주문에 여러 판매자 상품이 섞여 있어도 본인 항목 외에는 보이지 않음.
     * 금액 모델도 본인 항목 기준으로만 노출한다. 할인 수단이 아직 없어 할인액은 0,
     * 결제금액은 항목 합계와 같다 — 쿠폰·마일리지 도입 시 판매자별 비례 배분이 필요하다(§4.2).
     */
    public static OrderResponse forSeller(Order order, Long sellerId) {
        List<OrderItemResponse> myItems = order.getItems().stream()
                .filter(item -> item.isOwnedBy(sellerId))
                .map(OrderItemResponse::from)
                .toList();
        long sellerTotal = myItems.stream().mapToLong(OrderItemResponse::subtotal).sum();
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getDeliveryStatus(),
                sellerTotal,
                sellerTotal,
                0L,
                0L,
                sellerTotal,
                0L,
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
