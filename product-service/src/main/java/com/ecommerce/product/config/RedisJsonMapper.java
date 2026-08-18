package com.ecommerce.product.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Redis 저장 전용 JSON 변환기.
 *
 * <p>ObjectMapper 를 그대로 빈으로 노출하면 Spring Boot 자동설정의
 * jacksonObjectMapper({@code @ConditionalOnMissingBean})가 비활성화되어
 * HTTP 메시지 컨버터까지 Boot 커스터마이징이 빠진 raw 매퍼를 쓰게 된다.
 * 이를 막기 위해 Redis 전용 매퍼는 별도 타입으로 감싸 전역 노출을 차단한다.
 *
 * <p>직렬화 형식은 기존과 동일하다 — 타입 정보(@class) 없이 순수 JSON,
 * LocalDateTime 은 ISO-8601 문자열.
 */
public class RedisJsonMapper {

    private final ObjectMapper delegate;

    public RedisJsonMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.delegate = mapper;
    }

    /** 객체 → JSON 문자열 */
    public String writeValueAsString(Object value) throws JsonProcessingException {
        return delegate.writeValueAsString(value);
    }

    /** JSON 문자열 → 단일 타입 객체 */
    public <T> T readValue(String json, Class<T> type) throws JsonProcessingException {
        return delegate.readValue(json, type);
    }

    /** JSON 문자열 → 제네릭 타입 객체(List 등) */
    public <T> T readValue(String json, TypeReference<T> typeRef) throws JsonProcessingException {
        return delegate.readValue(json, typeRef);
    }
}
