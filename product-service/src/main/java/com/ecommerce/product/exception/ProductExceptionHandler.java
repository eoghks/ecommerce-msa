package com.ecommerce.product.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProductExceptionHandler {

    /** 위시리스트 추가 경로 판별용 — 이 맥락의 유니크 위반만 멱등 처리한다. */
    private static final String WISHLIST_ADD_PATH_PREFIX = "/api/v1/wishlist/";

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleProductNotFound(ProductNotFoundException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        pd.setTitle("Product Not Found");
        return pd;
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ProblemDetail handleCategoryNotFound(CategoryNotFoundException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        pd.setTitle("Category Not Found");
        return pd;
    }

    @ExceptionHandler(DuplicateCategoryNameException.class)
    public ProblemDetail handleDuplicateCategoryName(DuplicateCategoryNameException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        pd.setTitle("Duplicate Category Name");
        return pd;
    }

    @ExceptionHandler(CategoryInUseException.class)
    public ProblemDetail handleCategoryInUse(CategoryInUseException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        pd.setTitle("Category In Use");
        return pd;
    }

    @ExceptionHandler(NotProductOwnerException.class)
    public ProblemDetail handleNotOwner(NotProductOwnerException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        pd.setTitle("Forbidden");
        return pd;
    }

    @ExceptionHandler(MissingSellerIdException.class)
    public ProblemDetail handleMissingSellerId(MissingSellerIdException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        pd.setTitle("Unauthorized");
        return pd;
    }

    @ExceptionHandler(ProductNotOnSaleException.class)
    public ProblemDetail handleProductNotOnSale(ProductNotOnSaleException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        pd.setTitle("Product Not On Sale");
        return pd;
    }

    @ExceptionHandler(InvalidSearchParameterException.class)
    public ProblemDetail handleInvalidSearchParameter(InvalidSearchParameterException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        pd.setTitle("Invalid Search Parameter");
        return pd;
    }

    @ExceptionHandler(ReviewNotFoundException.class)
    public ProblemDetail handleReviewNotFound(ReviewNotFoundException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        pd.setTitle("Review Not Found");
        return pd;
    }

    @ExceptionHandler(NotReviewOwnerException.class)
    public ProblemDetail handleNotReviewOwner(NotReviewOwnerException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        pd.setTitle("Forbidden");
        return pd;
    }

    @ExceptionHandler(PurchaseRequiredException.class)
    public ProblemDetail handlePurchaseRequired(PurchaseRequiredException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        pd.setTitle("Purchase Required");
        return pd;
    }

    @ExceptionHandler(DuplicateReviewException.class)
    public ProblemDetail handleDuplicateReview(DuplicateReviewException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        pd.setTitle("Duplicate Review");
        return pd;
    }

    @ExceptionHandler(UnauthenticatedException.class)
    public ProblemDetail handleUnauthenticated(UnauthenticatedException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        pd.setTitle("Unauthorized");
        return pd;
    }

    /**
     * 데이터 무결성 위반 처리.
     * 위시리스트 추가(POST /api/v1/wishlist/{productId}) 동시 요청으로 유니크 제약
     * (uk_wishlist_user_product)이 깨지면 이미 찜된 상태이므로 204로 멱등 성공 처리한다.
     * 그 외 맥락의 무결성 위반은 409로 그대로 전달한다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
            DataIntegrityViolationException e, HttpServletRequest request) {
        if (isWishlistAddRequest(request)) {
            return ResponseEntity.noContent().build();
        }
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "데이터 무결성 제약을 위반했습니다.");
        pd.setTitle("Data Integrity Violation");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    /** POST /api/v1/wishlist/{productId} 여부 판별 */
    private boolean isWishlistAddRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().startsWith(WISHLIST_ADD_PATH_PREFIX);
    }
}
