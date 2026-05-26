package com.ecommerce.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
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

    // 비밀번호 변경 시 해당 유저의 모든 Refresh Token 무효화
    public void deleteAllByUserId(Long userId) {
        String userIdStr = String.valueOf(userId);
        ScanOptions options = ScanOptions.scanOptions().match(PREFIX + "*").count(100).build();
        redisTemplate.executeWithStickyConnection(conn ->
            conn.scan(options)
        ).forEachRemaining(key -> {
            String keyStr = new String(key);
            String value = redisTemplate.opsForValue().get(keyStr);
            if (userIdStr.equals(value)) {
                redisTemplate.delete(keyStr);
            }
        });
    }
}