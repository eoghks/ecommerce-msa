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
}
