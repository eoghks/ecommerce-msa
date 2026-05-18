package com.ecommerce.common.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 전역 예외 핸들러 — RFC 7807 ProblemDetail 형식으로 응답
 *
 * 사용 서비스: 각 서비스 Application 클래스에
 *   @SpringBootApplication(scanBasePackages = "com.ecommerce") 설정 필요
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERROR_TYPE_BASE = "https://ecommerce-msa.com/errors";

    /** @Valid / @Validated 필드 검증 실패 → 400 + errors[] */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldErrorDetail(fe.getField(), fe.getDefaultMessage()))
                .toList();

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다.");
        pd.setTitle("Validation Failed");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/validation"));
        pd.setProperty("errors", errors);
        return pd;
    }

    /** @Validated 파라미터 검증 실패 → 400 + errors[] */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorDetail> errors = ex.getConstraintViolations().stream()
                .map(cv -> {
                    String field = cv.getPropertyPath().toString();
                    return new FieldErrorDetail(field, cv.getMessage());
                })
                .toList();

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다.");
        pd.setTitle("Validation Failed");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/validation"));
        pd.setProperty("errors", errors);
        return pd;
    }

    /** 잘못된 인자 → 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Bad Request");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/bad-request"));
        return pd;
    }

    /** 리소스 없음 → 404 */
    @ExceptionHandler({NoSuchElementException.class, NoHandlerFoundException.class})
    public ProblemDetail handleNotFound(Exception ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Not Found");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/not-found"));
        return pd;
    }

    /** @PreAuthorize 인가 실패 → 403 */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ProblemDetail handleAuthorizationDenied(AuthorizationDeniedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        pd.setTitle("Access Denied");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/forbidden"));
        return pd;
    }

    /** 미처리 예외 → 500 (requestId 로그 포함) */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleException(Exception ex) {
        log.error("처리되지 않은 예외: requestId={}", MDC.get("requestId"), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");
        pd.setTitle("Internal Server Error");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/internal"));
        return pd;
    }
}
