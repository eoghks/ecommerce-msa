package com.ecommerce.order.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PG 주문번호 규칙 (payment-foundation §11-1).
 * 토스페이먼츠는 orderId 를 6~64자 문자열로 요구하므로 내부 주문 id 를 그대로 쓸 수 없다.
 *
 * M-05: 결제 실패 후 재시도는 같은 주문번호를 다시 쓸 수 없다(토스가 접수된 주문번호 재사용을 거부).
 * 그래서 시도마다 접미사가 붙은 새 번호를 쓰며(ORD-00000123-a1b2c3d4), 서버는 값을 재생성하는 대신
 * "규칙 + 주문 id 일치"를 검증하고 승인 준비 시 결제 레코드에 저장해 재조회·대조에 쓴다.
 * 프론트(`frontend/src/utils/pgOrderId.ts`)도 동일 규칙을 사용해야 승인이 성립한다.
 */
public final class PgOrderId {

    /** ORD- + 8자리 zero-pad (총 12자) — 토스 최소 길이(6자) 충족. 접미사 없는 형태도 허용한다 */
    private static final String FORMAT = "ORD-%08d";

    /** ORD-{주문id 8자리 이상}[-{시도 접미사 영숫자}] */
    private static final Pattern PATTERN = Pattern.compile("ORD-(\\d{8,19})(?:-([0-9A-Za-z]{1,20}))?");

    /** 토스 orderId 최대 길이 */
    private static final int MAX_LENGTH = 64;

    private PgOrderId() {
    }

    /** 내부 주문 id 로 만드는 기본 주문번호(접미사 없음) — 0원 결제·이력 보정용 */
    public static String of(Long orderId) {
        return String.format(FORMAT, orderId);
    }

    /**
     * M-05: 클라이언트가 보낸 PG 주문번호 검증.
     * 규칙에 맞고 주문 id 가 일치할 때만 통과시켜 다른 주문·임의 문자열로 승인이 나가는 것을 막는다.
     * @return 검증을 통과한 PG 주문번호(그대로 PG 승인·재조회에 사용)
     */
    public static String requireForOrder(String pgOrderId, Long orderId) {
        if (pgOrderId == null || orderId == null || pgOrderId.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("PG 주문번호가 올바르지 않습니다.");
        }
        Matcher matcher = PATTERN.matcher(pgOrderId);
        if (!matcher.matches() || Long.parseLong(matcher.group(1)) != orderId) {
            throw new IllegalArgumentException("PG 주문번호가 주문과 일치하지 않습니다. orderId=" + orderId);
        }
        return pgOrderId;
    }
}
