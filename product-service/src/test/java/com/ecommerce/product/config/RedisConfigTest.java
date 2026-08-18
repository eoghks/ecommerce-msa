package com.ecommerce.product.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-01 회귀 방지: Redis 전용 매퍼가 Boot 기본 ObjectMapper를 가리지 않는지 검증.
 */
@DisplayName("RedisConfig 단위 테스트 — Boot 기본 ObjectMapper 보존 (F-01)")
class RedisConfigTest {

    private static final String SAMPLE_JSON = "{\"at\":\"2026-08-18T10:15:30\"}";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withBean(RedisConnectionFactory.class, () -> Mockito.mock(RedisConnectionFactory.class))
            .withUserConfiguration(RedisConfig.class);

    @Test
    @DisplayName("ObjectMapper 빈은 Boot 자동설정(jacksonObjectMapper) 하나만 등록된다")
    void bootObjectMapperIsNotShadowed() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ObjectMapper.class);
            assertThat(context.getBeanNamesForType(ObjectMapper.class))
                    .containsExactly("jacksonObjectMapper");
        });
    }

    @Test
    @DisplayName("주입되는 ObjectMapper에 Boot 커스터마이저가 적용된다 — 알 수 없는 필드 무시 + JavaTimeModule")
    void bootObjectMapperAppliesBootDefaults() {
        contextRunner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            assertThat(mapper.getDeserializationConfig()
                    .isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();

            Sample sample = mapper.readValue("{\"at\":\"2026-08-18T10:15:30\",\"unknown\":1}", Sample.class);
            assertThat(sample.at()).isEqualTo(LocalDateTime.of(2026, 8, 18, 10, 15, 30));
        });
    }

    @Test
    @DisplayName("Redis 전용 매퍼는 별도 타입으로만 노출된다")
    void redisJsonMapperIsSeparateType() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RedisJsonMapper.class);
            assertThat(ObjectMapper.class.isAssignableFrom(RedisJsonMapper.class)).isFalse();
        });
    }

    @Test
    @DisplayName("Redis 직렬화 형식 유지 — 타입 정보 없이 ISO-8601 문자열")
    void redisJsonMapperKeepsSerializationFormat() throws Exception {
        RedisJsonMapper mapper = new RedisJsonMapper();

        String json = mapper.writeValueAsString(new Sample(LocalDateTime.of(2026, 8, 18, 10, 15, 30)));

        assertThat(json).isEqualTo(SAMPLE_JSON);
        assertThat(json).doesNotContain("@class");
    }

    @Test
    @DisplayName("Redis 전용 매퍼 — 제네릭(List) 역직렬화 지원")
    void redisJsonMapperReadsGenericType() throws Exception {
        RedisJsonMapper mapper = new RedisJsonMapper();

        List<Sample> samples = mapper.readValue("[" + SAMPLE_JSON + "]", new TypeReference<>() {});

        assertThat(samples).hasSize(1);
        assertThat(samples.get(0).at()).isEqualTo(LocalDateTime.of(2026, 8, 18, 10, 15, 30));
    }

    /** 직렬화 형식 검증용 샘플 DTO */
    record Sample(LocalDateTime at) {}
}
