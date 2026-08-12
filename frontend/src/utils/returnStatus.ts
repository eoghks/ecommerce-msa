import type { ReturnStatus } from '../types';

/** 상태 뱃지 표시 스타일 */
export interface ReturnStatusStyle {
  text: string;
  color: string;
}

// V1.1-5: 반품 상태 뱃지 라벨 — 내 주문/내 반품/반품 관리 화면 공용
const RETURN_STATUS_LABEL: Record<string, ReturnStatusStyle> = {
  REQUESTED: { text: '반품접수', color: '#f59e0b' },
  APPROVED:  { text: '반품승인', color: '#22c55e' },
  REJECTED:  { text: '반품거부', color: '#9ca3af' },
  REFUNDED:  { text: '환불완료', color: '#0ea5e9' },
};

/** 반품 상태 뱃지 스타일 조회 — 미지정 상태는 회색 폴백 */
export const returnStatusStyle = (status: ReturnStatus): ReturnStatusStyle =>
  RETURN_STATUS_LABEL[status] ?? { text: status, color: '#6b7280' };

// 동일 항목 재신청을 막는 진행 상태 (백엔드 ReturnStatus.activeStatuses()와 동일 집합)
const BLOCKING_STATUSES: ReturnStatus[] = ['REQUESTED', 'APPROVED', 'REFUNDED'];

/** 해당 반품 건이 재신청을 막는 상태인지 — REJECTED 는 사유 보완 후 재신청 허용 */
export const blocksNewReturn = (status: ReturnStatus): boolean =>
  BLOCKING_STATUSES.includes(status);
