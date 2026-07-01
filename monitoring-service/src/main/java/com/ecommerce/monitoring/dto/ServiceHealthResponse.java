package com.ecommerce.monitoring.dto;

/**
 * 서비스 헬스 상태 응답 DTO
 *
 * @param name           서비스 이름
 * @param status         상태 (UP/DOWN)
 * @param responseTimeMs 응답 시간(ms)
 * @param error          실패 사유 (정상 시 null)
 */
public record ServiceHealthResponse(
        String name,
        String status,
        long responseTimeMs,
        String error
) {
    /** 정상 응답 */
    public static ServiceHealthResponse up(String name, long responseTimeMs) {
        return new ServiceHealthResponse(name, "UP", responseTimeMs, null);
    }

    /** 실패 응답 */
    public static ServiceHealthResponse down(String name, long responseTimeMs, String error) {
        return new ServiceHealthResponse(name, "DOWN", responseTimeMs, error);
    }
}
