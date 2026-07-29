package com.ecommerce.order.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
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

    /** 반품 없음/타인 소유 반품 접근 → 404 Not Found (정보 노출 방지) */
    @ExceptionHandler(ReturnRequestNotFoundException.class)
    public ProblemDetail handleReturnRequestNotFound(ReturnRequestNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Return Request Not Found");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/return-not-found"));
        return pd;
    }

    /** 반품 자격 미충족(배송완료 아님·취소된 항목·사유 누락) → 400 Bad Request */
    @ExceptionHandler(ReturnNotAllowedException.class)
    public ProblemDetail handleReturnNotAllowed(ReturnNotAllowedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Return Not Allowed");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/return-not-allowed"));
        return pd;
    }

    /** 잘못된 반품 상태 전이 → 400 Bad Request */
    @ExceptionHandler(InvalidReturnStatusException.class)
    public ProblemDetail handleInvalidReturnStatus(InvalidReturnStatusException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Invalid Return Status");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/invalid-return-status"));
        return pd;
    }

    /** 동일 항목 반품 중복 신청 → 409 Conflict */
    @ExceptionHandler(DuplicateReturnRequestException.class)
    public ProblemDetail handleDuplicateReturnRequest(DuplicateReturnRequestException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Duplicate Return Request");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/duplicate-return-request"));
        return pd;
    }

    /** 반품 처리·조회 권한 없음 → 403 Forbidden */
    @ExceptionHandler(ReturnAccessDeniedException.class)
    public ProblemDetail handleReturnAccessDenied(ReturnAccessDeniedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setTitle("Forbidden");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/return-forbidden"));
        return pd;
    }

    /**
     * H-2: DB 유니크 제약 등 데이터 무결성 위반 → 409 Conflict.
     * 반품 중복 신청은 서비스 검증(exists)과 save 사이에 원자성이 없어 동시 요청 시
     * 부분 유니크 인덱스(uq_return_item_active)가 최종 방어선이 된다.
     * 이때 기본 500 대신 중복 신청임을 알 수 있는 409 로 전달한다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "이미 처리 중인 요청이 있습니다. 잠시 후 다시 확인해주세요.");
        pd.setTitle("Data Integrity Violation");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/duplicate-return-request"));
        return pd;
    }

    /**
     * M-1: 동시 처리 충돌(낙관적 락 버전 불일치) → 409 Conflict.
     * 반품 승인/거부가 동시에 들어오면 뒤늦은 트랜잭션이 실패하도록 하여
     * 상태 유실·환불 훅 중복 호출을 막는다.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLockingFailure(OptimisticLockingFailureException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "다른 처리가 먼저 완료되었습니다. 최신 상태를 확인한 뒤 다시 시도해주세요.");
        pd.setTitle("Concurrent Modification");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/concurrent-modification"));
        return pd;
    }

    /** L-1: 잘못된 인자(주문에 없는 항목 id 등) → 400 Bad Request */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Invalid Request");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/invalid-request"));
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
