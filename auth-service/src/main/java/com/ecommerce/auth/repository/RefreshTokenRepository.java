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

    /**
     * H-B: 토큰 회전용 원자적 조회+삭제(Redis GETDEL).
     * 동시 요청이 같은 refresh 토큰으로 들어와도 값을 얻는 요청은 단 하나뿐 →
     * 나머지는 empty를 받아 거부된다(TOCTOU 방지, 재사용 탐지).
     * @return 토큰에 매핑된 userId (없거나 이미 소비됐으면 empty)
     */
    public Optional<Long> findAndDeleteUserId(String refreshToken) {
        String value = redisTemplate.opsForValue().getAndDelete(PREFIX + refreshToken);
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