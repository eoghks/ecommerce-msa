package com.ecommerce.product.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * 캐시 직렬화용 JSON 변환기.
     * 타입 정보(@class) 없이 순수 JSON 저장 — 패키지 변경에 안전.
     * LocalDateTime → ISO-8601 문자열 처리.
     * ObjectMapper 타입으로 노출하면 Boot 기본 ObjectMapper 자동설정이 꺼지므로 전용 타입을 쓴다.
     */
    @Bean
    public RedisJsonMapper redisJsonMapper() {
        return new RedisJsonMapper();
    }

    /**
     * 키/값 모두 String 직렬화.
     * 서비스에서 RedisJsonMapper로 직접 JSON 변환 후 저장 — 타입 안전.
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        return template;
    }
}
