package com.ecommerce.order.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StatsPeriod 통계 기간 단위 테스트 (V1.1-7)")
class StatsPeriodTest {

    @Test
    @DisplayName("from/to 미지정 시 오늘을 포함한 최근 30일")
    void of_default_last30Days() {
        StatsPeriod period = StatsPeriod.of(null, null);

        LocalDate today = LocalDate.now();
        assertThat(period.to()).isEqualTo(today);
        assertThat(period.from()).isEqualTo(today.minusDays(29));
        assertThat(period.dates()).hasSize(StatsPeriod.DEFAULT_DAYS);
    }

    @Test
    @DisplayName("to만 지정하면 해당 날짜 기준 최근 30일")
    void of_onlyTo_last30Days() {
        LocalDate to = LocalDate.of(2026, 7, 31);

        StatsPeriod period = StatsPeriod.of(null, to);

        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 7, 2));
        assertThat(period.to()).isEqualTo(to);
    }

    @Test
    @DisplayName("from만 지정하면 종료일은 오늘")
    void of_onlyFrom_toIsToday() {
        LocalDate from = LocalDate.now().minusDays(3);

        StatsPeriod period = StatsPeriod.of(from, null);

        assertThat(period.from()).isEqualTo(from);
        assertThat(period.to()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("조회 구간은 시작일 00:00 이상 ~ 종료일 다음날 00:00 미만 (종료일 포함)")
    void periodBoundary_includesToDate() {
        StatsPeriod period = StatsPeriod.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertThat(period.fromAt()).isEqualTo(LocalDate.of(2026, 7, 1).atStartOfDay());
        assertThat(period.toAtExclusive()).isEqualTo(LocalDate.of(2026, 8, 1).atStartOfDay());
    }

    @Test
    @DisplayName("from == to 는 하루 조회로 허용")
    void of_sameDay_allowed() {
        LocalDate day = LocalDate.of(2026, 7, 15);

        StatsPeriod period = StatsPeriod.of(day, day);

        assertThat(period.dates()).containsExactly(day);
    }

    @Test
    @DisplayName("from > to 이면 잘못된 요청(400)")
    void of_fromAfterTo_throws() {
        assertThatThrownBy(() -> StatsPeriod.of(LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("종료일");
    }

    @Test
    @DisplayName("366일은 허용, 367일은 잘못된 요청(400)")
    void of_maxDaysBoundary() {
        LocalDate to = LocalDate.of(2026, 12, 31);

        StatsPeriod allowed = StatsPeriod.of(to.minusDays(StatsPeriod.MAX_DAYS - 1L), to);
        assertThat(allowed.dates()).hasSize(StatsPeriod.MAX_DAYS);

        assertThatThrownBy(() -> StatsPeriod.of(to.minusDays(StatsPeriod.MAX_DAYS), to))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최대");
    }

    @Test
    @DisplayName("dates()는 기간 내 모든 날짜를 오름차순으로 반환")
    void dates_ascending() {
        StatsPeriod period = StatsPeriod.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 4));

        assertThat(period.dates()).containsExactly(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2),
                LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 4));
    }
}
