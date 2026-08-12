package com.ecommerce.order.support;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * 서비스 기준 타임존 고정 (M-3).
 *
 * 주문 생성 시각(@CreatedDate LocalDateTime)과 통계 집계(LocalDate 기준)는 모두
 * JVM 기본 타임존에 좌우된다. 컨테이너 기본값인 UTC 로 기동되면 KST 00:00~09:00 주문이
 * 전날로 기록되어 조회 종료일 당일 데이터가 최대 9시간 누락된다.
 * 따라서 기동 시점에 JVM 기본 타임존을 KST 로 고정한다(호스트 TZ 설정과 무관하게 동일 결과).
 */
public final class ServiceTimeZone {

    /** 서비스 기준 타임존 — 주문 시각 기록·통계 집계 기준 */
    public static final String ZONE_ID = "Asia/Seoul";

    private ServiceTimeZone() {
    }

    /** 기준 타임존 */
    public static ZoneId zone() {
        return ZoneId.of(ZONE_ID);
    }

    /** JVM 기본 타임존을 기준 타임존으로 고정한다 — 애플리케이션 기동 최초에 호출한다. */
    public static void applyDefault() {
        TimeZone.setDefault(TimeZone.getTimeZone(zone()));
    }
}
