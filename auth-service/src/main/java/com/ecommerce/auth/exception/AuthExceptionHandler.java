package com.ecommerce.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Auth Service 전용 예외 핸들러
 * GlobalExceptionHandler보다 구체적인 예외를 먼저 처리 (Spring MVC 우선순위)
 */
@RestControllerAdvice
public class AuthExceptionHandler {

    private static final String ERROR_TYPE_BASE = "https://ecommerce-msa.com/errors";

    /** 이메일 중복 → 409 Conflict */
    @ExceptionHandler(DuplicateEmailException.class)
    public ProblemDetail handleDuplicateEmail(DuplicateEmailException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Duplicate Email");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/duplicate-email"));
        return pd;
    }

    /** 이메일/비밀번호 불일치 → 401 Unauthorized */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        pd.setTitle("Invalid Credentials");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/invalid-credentials"));
        return pd;
    }

    /** 만료/유효하지 않은 토큰 → 401 Unauthorized */
    @ExceptionHandler(InvalidTokenException.class)
    public ProblemDetail handleInvalidToken(InvalidTokenException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        pd.setTitle("Invalid Token");
        pd.setType(URI.create(ERROR_TYPE_BASE + "/invalid-token"));
        return pd;
    }
}
