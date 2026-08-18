package com.ecommerce.order.support;

import com.ecommerce.order.dto.StatsPeriod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("통계 집계 기준 타임존 고정 테스트 (M-3)")
class StatsTimeZoneTest {

    private static final ZoneId KST = ZoneId.of(ServiceTimeZone.ZONE_ID);

    private TimeZone original;

    @BeforeEach
    void backupDefault() {
        original = TimeZone.getDefault();
    }

    @AfterEach
    void restoreDefault() {
        TimeZone.setDefault(original);
    }

    @Test
    @DisplayName("UTC 로 기동된 JVM 이어도 기본 타임존을 KST 로 고정한다")
    void applyDefault_fixesJvmTimeZoneToKst() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));

        ServiceTimeZone.applyDefault();

        assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(KST);
        assertThat(ServiceTimeZone.zone()).isEqualTo(KST);
    }

    @Test
    @DisplayName("타임존 고정 후 기간 기본값의 종료일은 KST 기준 오늘")
    void statsPeriodDefault_usesKstToday() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
        ServiceTimeZone.applyDefault();

        StatsPeriod period = StatsPeriod.of(null, null);

        assertThat(period.to()).isEqualTo(LocalDate.now(KST));
        assertThat(period.from()).isEqualTo(LocalDate.now(KST).minusDays(StatsPeriod.DEFAULT_DAYS - 1L));
    }

    @Test
    @DisplayName("KST 새벽 주문은 UTC 기준으로 전날로 기록된다 — 타임존 고정이 필요한 이유")
    void utcDefault_shiftsEarlyMorningOrderToPreviousDay() {
        Instant earlyMorningKst = LocalDateTime.of(2026, 8, 12, 0, 30).atZone(KST).toInstant();

        LocalDate recordedInUtc = LocalDateTime.ofInstant(earlyMorningKst, ZoneOffset.UTC).toLocalDate();
        LocalDate recordedInKst = LocalDateTime.ofInstant(earlyMorningKst, KST).toLocalDate();

        assertThat(recordedInUtc).isEqualTo(LocalDate.of(2026, 8, 11));
        assertThat(recordedInKst).isEqualTo(LocalDate.of(2026, 8, 12));
    }
}
