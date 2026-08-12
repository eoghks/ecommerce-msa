package com.ecommerce.order.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

/**
 * 통계 조회 기간 (V1.1-7).
 * from/to 는 ISO date 이며 to 를 포함한다(마감일 하루 전체 포함).
 * 미지정 시 최근 30일, from > to 또는 366일 초과는 잘못된 요청(400)이다.
 *
 * V1.2-7 판매자 인사이트는 이 기간 값을 그대로 재사용하고 판매자 필터만 추가한다.
 */
public record StatsPeriod(LocalDate from, LocalDate to) {

    /** 기간 미지정 시 기본 조회 일수 (오늘 포함 최근 30일) */
    public static final int DEFAULT_DAYS = 30;

    /** 최대 조회 가능 일수 — 초과 시 400 */
    public static final int MAX_DAYS = 366;

    /**
     * 기간 확정 — null 은 기본값으로 채우고 유효성을 검증한다.
     * @param from 시작일(null 이면 to 기준 최근 30일)
     * @param to   종료일(null 이면 오늘), 포함
     */
    public static StatsPeriod of(LocalDate from, LocalDate to) {
        LocalDate resolvedTo   = (to == null) ? LocalDate.now() : to;
        LocalDate resolvedFrom = (from == null) ? resolvedTo.minusDays(DEFAULT_DAYS - 1L) : from;
        validate(resolvedFrom, resolvedTo);
        return new StatsPeriod(resolvedFrom, resolvedTo);
    }

    private static void validate(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("조회 시작일이 종료일보다 늦을 수 없습니다.");
        }
        if (days(from, to) > MAX_DAYS) {
            throw new IllegalArgumentException("조회 기간은 최대 " + MAX_DAYS + "일까지 가능합니다.");
        }
    }

    /** 조회 시작 시각 (시작일 00:00:00) */
    public LocalDateTime fromAt() {
        return from.atStartOfDay();
    }

    /** 조회 종료 시각(미포함) — 종료일 다음날 00:00:00 으로 종료일 하루 전체를 포함한다 */
    public LocalDateTime toAtExclusive() {
        return to.plusDays(1).atStartOfDay();
    }

    /** 기간 내 전체 날짜 (오름차순) — 데이터 없는 날을 0으로 채울 때 사용 */
    public List<LocalDate> dates() {
        return Stream.iterate(from, date -> date.plusDays(1))
                .limit(days(from, to))
                .toList();
    }

    private static long days(LocalDate from, LocalDate to) {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }
}
