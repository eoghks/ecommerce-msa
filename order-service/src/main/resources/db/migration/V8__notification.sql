-- ============================================================
-- V8: 알림(notification) 테이블 — 주문·배송 인앱 알림 (V1.1-4)
-- ============================================================

-- 사용자 대상 인앱 알림. type 은 NotificationType enum 문자열 저장.
-- title/message 는 서버에서 타입별 상수 템플릿으로 조립(개인정보 최소, 주문번호 수준만).
CREATE TABLE notification (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    type        VARCHAR(40) NOT NULL,
    title       VARCHAR(200) NOT NULL,
    message     VARCHAR(500) NOT NULL,
    order_id    BIGINT,          -- 관련 주문(클릭 시 이동)
    is_read     BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- 내 알림 목록(최신순) 조회용
CREATE INDEX idx_notification_user ON notification (user_id, created_at DESC);
-- 미읽음 수(뱃지) 조회용 부분 인덱스
CREATE INDEX idx_notification_user_unread ON notification (user_id) WHERE is_read = false;
