package com.ecommerce.order.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class OrderExceptionHandler {

    private static final String ERROR_TYPE_BASE = "https://ecommerce-msa.com/errors";

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleOrderNotFound(OrderNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Order Not Found");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/order-not-found"));
        return pd;
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleProductNotFound(ProductNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Product Not Found");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/product-not-found"));
        return pd;
    }

    @ExceptionHandler(CartItemNotFoundException.class)
    public ProblemDetail handleCartItemNotFound(CartItemNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Cart Item Not Found");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/cart-item-not-found"));
        return pd;
    }

    @ExceptionHandler(OrderItemNotFoundException.class)
    public ProblemDetail handleOrderItemNotFound(OrderItemNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Order Item Not Found");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/order-item-not-found"));
        return pd;
    }

    /** 알림 없음/타인 소유 알림 접근 → 404 Not Found (정보 노출 방지) */
    @ExceptionHandler(NotificationNotFoundException.class)
    public ProblemDetail handleNotificationNotFound(NotificationNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Notification Not Found");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/notification-not-found"));
        return pd;
    }

    /** 내부 서비스 인증 실패 → 403 Forbidden */
    @ExceptionHandler(InvalidInternalTokenException.class)
    public ProblemDetail handleInvalidInternalToken(InvalidInternalTokenException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setTitle("Invalid Internal Token");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/invalid-internal-token"));
        return pd;
    }

    /** 본인 항목이 아닌 주문 항목 취소 시도 → 403 Forbidden */
    @ExceptionHandler(OrderItemAccessDeniedException.class)
    public ProblemDetail handleOrderItemAccessDenied(OrderItemAccessDeniedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setTitle("Forbidden");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/order-item-forbidden"));
        return pd;
    }

    /** 취소 불가 상태 주문 취소 시도 → 409 Conflict */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Order State Conflict");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/order-state-conflict"));
        return pd;
    }

    /** 배송지 조회 실패(없음/타인 소유) → 404 Not Found */
    @ExceptionHandler(AddressNotFoundException.class)
    public ProblemDetail handleAddressNotFound(AddressNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Address Not Found");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/address-not-found"));
        return pd;
    }

    /** 인증 정보(X-User-Id) 부재 → 401 Unauthorized */
    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        pd.setTitle("Unauthorized");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/unauthorized"));
        return pd;
    }

    /** 잘못된 배송상태 전이/대상 아닌 주문 → 400 Bad Request */
    @ExceptionHandler(InvalidDeliveryStatusException.class)
    public ProblemDetail handleInvalidDeliveryStatus(InvalidDeliveryStatusException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Invalid Delivery Status");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/invalid-delivery-status"));
        return pd;
    }

    /** 배송상태 변경 권한 없음 → 403 Forbidden */
    @ExceptionHandler(DeliveryStatusAccessDeniedException.class)
    public ProblemDetail handleDeliveryStatusAccessDenied(DeliveryStatusAccessDeniedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setTitle("Forbidden");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/delivery-status-forbidden"));
        return pd;
    }

    /** 주문 배송지 정보 유효하지 않음(addressId 무효/직접입력 누락) → 400 Bad Request */
    @ExceptionHandler(InvalidOrderShippingException.class)
    public ProblemDetail handleInvalidOrderShipping(InvalidOrderShippingException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Invalid Order Shipping");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/invalid-order-shipping"));
        return pd;
    }
}
