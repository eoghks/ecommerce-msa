package com.ecommerce.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;

    public void save(String refreshToken, Long userId, Duration ttl) {
        redisTemplate.opsForValue().set(PREFIX + refreshToken, String.valueOf(userId), ttl);
    }

    public Optional<Long> findUserIdByToken(String refreshToken) {
        String value = redisTemplate.opsForValue().get(PREFIX + refreshToken);
        return Optional.ofNullable(value).map(Long::parseLong);
    }

    public void delete(String refreshToken) {
        redisTemplate.delete(PREFIX + refreshToken);
    }
}