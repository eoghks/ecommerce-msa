package com.ecommerce.order.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/** D-14: 공통 GlobalExceptionHandler(최하위)의 catch-all보다 먼저 조회되도록 우선순위를 명시 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 100)
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

    /** 구매확정 자격 미충족(배송완료 전) → 400 Bad Request */
    @ExceptionHandler(PurchaseConfirmNotAllowedException.class)
    public ProblemDetail handlePurchaseConfirmNotAllowed(PurchaseConfirmNotAllowedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Purchase Confirm Not Allowed");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/purchase-confirm-not-allowed"));
        return pd;
    }

    /**
     * 이미 구매확정된 주문 재확정 → 409 Conflict.
     * 요청 자체는 유효하고 현재 리소스 상태와 충돌하는 경우이므로 반품 상태충돌(409)과 동일하게 처리한다.
     */
    @ExceptionHandler(PurchaseAlreadyConfirmedException.class)
    public ProblemDetail handlePurchaseAlreadyConfirmed(PurchaseAlreadyConfirmedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Purchase Already Confirmed");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/purchase-already-confirmed"));
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

    /**
     * 잘못된 반품 상태 전이 → 409 Conflict.
     * 요청 자체는 유효하고 현재 리소스 상태와 충돌하는 경우(이미 처리된 반품 재승인·재거부,
     * 동시 승인 시 패자 트랜잭션)이므로 400이 아닌 409로 응답한다.
     * 클라이언트는 재입력이 아니라 목록 새로고침으로 최신 상태를 확인해야 한다.
     */
    @ExceptionHandler(InvalidReturnStatusException.class)
    public ProblemDetail handleInvalidReturnStatus(InvalidReturnStatusException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
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

    /** 결제 정보 없음/타인 결제 접근 → 404 Not Found (정보 노출 방지) */
    @ExceptionHandler(PaymentNotFoundException.class)
    public ProblemDetail handlePaymentNotFound(PaymentNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Payment Not Found");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/payment-not-found"));
        return pd;
    }

    /** V1.1-6: 승인 요청 금액이 서버 계산 결제금액과 불일치 → 400 Bad Request (위변조 차단) */
    @ExceptionHandler(PaymentAmountMismatchException.class)
    public ProblemDetail handlePaymentAmountMismatch(PaymentAmountMismatchException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Payment Amount Mismatch");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/payment-amount-mismatch"));
        return pd;
    }

    /** V1.1-6: 이미 승인된 주문의 재승인 → 409 Conflict (주문당 승인 1건) */
    @ExceptionHandler(PaymentAlreadyApprovedException.class)
    public ProblemDetail handlePaymentAlreadyApproved(PaymentAlreadyApprovedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Payment Already Approved");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/payment-already-approved"));
        return pd;
    }

    /**
     * V1.1-6: PG 승인 실패(카드 거절·통신 오류) → 400 Bad Request.
     * 사용자가 다른 결제수단으로 재시도할 수 있도록 PG 사유 메시지를 그대로 전달한다.
     */
    @ExceptionHandler(PaymentApprovalFailedException.class)
    public ProblemDetail handlePaymentApprovalFailed(PaymentApprovalFailedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Payment Approval Failed");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/payment-approval-failed"));
        return pd;
    }

    /**
     * V1.1-6: PG 취소·환불 실패 → 502 Bad Gateway.
     * 우리 요청은 유효했고 외부 PG 처리가 실패한 경우이므로 재시도 대상임을 상태코드로 구분한다.
     */
    @ExceptionHandler(PaymentCancelFailedException.class)
    public ProblemDetail handlePaymentCancelFailed(PaymentCancelFailedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        pd.setTitle("Payment Cancel Failed");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/payment-cancel-failed"));
        return pd;
    }

    /**
     * C-01/M-04: PG 통신 실패(타임아웃·연결 오류) → 502 Bad Gateway.
     * 카드 거절(400)과 달리 우리 요청은 유효했고 결과가 미확정이므로 상태코드로 구분한다 —
     * 클라이언트는 재입력이 아니라 결제 상태 확인·재시도를 안내해야 한다.
     */
    @ExceptionHandler(PaymentGatewayUnavailableException.class)
    public ProblemDetail handlePaymentGatewayUnavailable(PaymentGatewayUnavailableException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        pd.setTitle("Payment Gateway Unavailable");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/payment-gateway-unavailable"));
        return pd;
    }

    /**
     * H-03: PG 응답이 승인 완료(DONE)가 아니거나 요청과 대조되지 않음 → 502 Bad Gateway.
     * 가상계좌 입금대기 등 미지원 응답을 결제완료로 확정하지 않았음을 알린다(승인분은 보상 취소).
     */
    @ExceptionHandler(PaymentNotCompletedException.class)
    public ProblemDetail handlePaymentNotCompleted(PaymentNotCompletedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        pd.setTitle("Payment Not Completed");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/payment-not-completed"));
        return pd;
    }

    /** M-03: 같은 주문에 진행 중·미확정 결제가 있음 → 409 Conflict (이중 승인 차단) */
    @ExceptionHandler(PaymentInProgressException.class)
    public ProblemDetail handlePaymentInProgress(PaymentInProgressException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Payment In Progress");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/payment-in-progress"));
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
