/**
 * PG 주문번호 규칙 (payment-foundation §11-1).
 * 토스페이먼츠는 orderId 를 6~64자 문자열로 요구하므로 내부 주문 id 를 그대로 쓸 수 없다.
 * 서버(`PgOrderId.java`)와 동일한 규칙이어야 승인이 성립한다 — 한쪽만 바꾸면 승인이 거부된다.
 */

/** ORD- + 8자리 zero-pad (총 12자) */
const PREFIX = 'ORD-';
const DIGITS = 8;

/** 내부 주문 id → PG 주문번호 */
export const toPgOrderId = (orderId: number): string =>
  `${PREFIX}${String(orderId).padStart(DIGITS, '0')}`;

/**
 * PG 주문번호 → 내부 주문 id.
 * 결제 성공·실패 리다이렉트는 PG 주문번호만 돌려주므로 여기서 되돌린다.
 * 규칙에 맞지 않으면 null — 호출부가 안내 문구를 띄운다(임의 값 신뢰 금지).
 */
export const toOrderId = (pgOrderId: string | null): number | null => {
  if (!pgOrderId || !pgOrderId.startsWith(PREFIX)) return null;
  const digits = pgOrderId.slice(PREFIX.length);
  if (!/^\d+$/.test(digits)) return null;
  const parsed = Number(digits);
  return parsed > 0 ? parsed : null;
};
