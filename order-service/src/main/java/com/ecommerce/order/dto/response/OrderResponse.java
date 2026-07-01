package com.ecommerce.order.dto.response;

import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        Long userId,
        OrderStatus status,
        Long totalPrice,
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
                order.getTotalPrice(),
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
                sellerTotal,
                myItems,
                order.getReceiver(),
                order.getPhone(),
                order.getAddress(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
