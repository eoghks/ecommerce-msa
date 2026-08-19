/**
 * PG 주문번호 규칙 (payment-foundation §11-1).
 * 토스페이먼츠는 orderId 를 6~64자 문자열로 요구하므로 내부 주문 id 를 그대로 쓸 수 없다.
 * 서버(`PgOrderId.java`)와 동일한 규칙이어야 승인이 성립한다 — 한쪽만 바꾸면 승인이 거부된다.
 *
 * M-05: 결제 실패 후 재시도에 같은 주문번호를 다시 쓰면 토스가 접수된 주문번호 재사용을 거부해
 * 재결제가 막힐 수 있다. 그래서 시도마다 임의 접미사를 붙이고(ORD-00000123-a1b2c3d4),
 * 승인 요청 시 이 값을 서버에 함께 보낸다(서버는 규칙·주문 id 일치를 검증한 뒤 저장한다).
 */

/** ORD- + 8자리 zero-pad + '-' + 시도 접미사 */
const PREFIX = 'ORD-';
const DIGITS = 8;
const ATTEMPT_LENGTH = 8;
const BASE36 = 36;

/** ORD-{주문id}[-{시도 접미사}] — 접미사 없는 이력 값도 해석한다 */
const PG_ORDER_ID_PATTERN = /^ORD-(\d{8,19})(?:-[0-9A-Za-z]{1,20})?$/;

/** 시도 접미사 — 예측 불가한 영숫자. 결제 시도마다 새로 만든다 */
const attemptSuffix = (): string => {
  const bytes = new Uint8Array(ATTEMPT_LENGTH);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (byte) => (byte % BASE36).toString(BASE36)).join('');
};

/** 내부 주문 id → 이번 결제 시도의 PG 주문번호 */
export const newPgOrderId = (orderId: number): string =>
  `${PREFIX}${String(orderId).padStart(DIGITS, '0')}-${attemptSuffix()}`;

/**
 * PG 주문번호 → 내부 주문 id.
 * 결제 성공·실패 리다이렉트는 PG 주문번호만 돌려주므로 여기서 되돌린다.
 * 규칙에 맞지 않으면 null — 호출부가 안내 문구를 띄운다(임의 값 신뢰 금지).
 */
export const toOrderId = (pgOrderId: string | null): number | null => {
  const matched = pgOrderId?.match(PG_ORDER_ID_PATTERN);
  if (!matched) return null;
  const parsed = Number(matched[1]);
  return parsed > 0 ? parsed : null;
};
