package com.ecommerce.auth.exception;

/**
 * 서비스 간 내부 호출 검증 실패 예외.
 * X-Internal-Token 헤더가 없거나 공유 시크릿과 불일치할 때 발생.
 */
public class InvalidInternalTokenException extends RuntimeException {

    public InvalidInternalTokenException() {
        super("내부 서비스 인증에 실패했습니다.");
    }
}
