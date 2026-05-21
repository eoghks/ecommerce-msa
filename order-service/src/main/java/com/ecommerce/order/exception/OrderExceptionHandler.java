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

    /** 취소 불가 상태 주문 취소 시도 → 409 Conflict */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Order State Conflict");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/order-state-conflict"));
        return pd;
    }
}
