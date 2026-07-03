package com.ecommerce.product.repository;

/**
 * LIKE 패턴 이스케이프 유틸.
 * 사용자 입력의 LIKE 메타문자(%, _)와 이스케이프 문자를 이스케이프해
 * 의도치 않은 광역 매칭/성능 저하를 방지한다. 이스케이프 문자는 '!'로 고정하며
 * 쿼리에서 ESCAPE 절과 함께 사용한다.
 */
public final class LikePatternEscaper {

    /** LIKE ESCAPE 문자 — 쿼리의 like(..., ESCAPE_CHAR)와 일치해야 함 */
    public static final char ESCAPE_CHAR = '!';

    private LikePatternEscaper() {
    }

    /** 원문의 !, %, _ 를 이스케이프해 LIKE 패턴에 안전한 문자열로 변환 */
    public static String escape(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (char c : raw.toCharArray()) {
            if (c == ESCAPE_CHAR || c == '%' || c == '_') {
                sb.append(ESCAPE_CHAR);
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** prefix(자동완성) 검색용 패턴: escaped + '%' */
    public static String startsWithPattern(String raw) {
        return escape(raw) + '%';
    }

    /** 부분일치(목록 검색)용 패턴: '%' + escaped + '%' */
    public static String containsPattern(String raw) {
        return '%' + escape(raw) + '%';
    }
}
