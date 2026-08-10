package com.ecommerce.common.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 전역 예외 핸들러 — RFC 7807 ProblemDetail 형식으로 응답
 *
 * 사용 서비스: 각 서비스 Application 클래스에
 *   @SpringBootApplication(scanBasePackages = "com.ecommerce") 설정 필요
 *
 * D-14: 서비스별 advice(AuthExceptionHandler 등)보다 항상 뒤에 조회되도록 최하위 우선순위를 명시한다.
 * (catch-all 핸들러가 서비스 고유 예외를 먼저 삼키는 것을 방지)
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
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

    /**
     * F-01: 매핑된 핸들러가 없는 경로 → 404.
     * 요청 경로를 그대로 되돌려주지 않도록 일반화된 메시지만 응답한다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(NoResourceFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "요청하신 리소스를 찾을 수 없습니다.");
        pd.setTitle("Not Found");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/not-found"));
        return pd;
    }

    /**
     * F-01: 요청 본문 해석 실패(깨진 JSON·알 수 없는 필드·enum 변환 실패) → 400.
     * 예외 메시지에 요청 본문 일부가 포함될 수 있어 로그·응답 모두에 원본을 노출하지 않는다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("요청 본문 해석 실패: requestId={}", MDC.get("requestId"));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "요청 본문을 해석할 수 없습니다. 형식과 값을 확인해주세요.");
        pd.setTitle("Malformed Request");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/malformed-request"));
        return pd;
    }

    /** F-01: 요청 파라미터 타입 불일치(enum·숫자 변환 실패 등) → 400 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "요청 파라미터 형식이 올바르지 않습니다: " + ex.getName());
        pd.setTitle("Invalid Parameter");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/invalid-parameter"));
        return pd;
    }

    /** F-01: 필수 쿼리 파라미터 누락 → 400 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "필수 요청 파라미터가 없습니다: " + ex.getParameterName());
        pd.setTitle("Missing Parameter");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/missing-parameter"));
        return pd;
    }

    /** F-01: multipart 파트(파일) 누락 → 400 */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ProblemDetail handleMissingPart(MissingServletRequestPartException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "필수 요청 항목이 없습니다: " + ex.getRequestPartName());
        pd.setTitle("Missing Request Part");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/missing-request-part"));
        return pd;
    }

    /** F-05: 업로드 용량 상한 초과 → 413 (클라이언트가 "파일이 너무 큽니다"를 안내할 수 있도록) */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
                "업로드 가능한 최대 크기를 초과했습니다.");
        pd.setTitle("Payload Too Large");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/payload-too-large"));
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
