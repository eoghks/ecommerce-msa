package com.ecommerce.common.exception;

/**
 * ProblemDetail errors[] 항목 — 필드 단위 유효성 검증 오류
 * 프론트엔드 React Hook Form setError(field, { message }) 와 매핑
 */
public record FieldErrorDetail(String field, String message) {}
