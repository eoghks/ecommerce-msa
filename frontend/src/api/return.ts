import type { AxiosResponse } from 'axios';
import api from './axios';
import type { Page, ReturnRequest } from '../types';

// V1.1-5: 반품 신청 (주문 소유자) — 사유 필수, 배송완료 주문의 활성 항목만
export const requestReturn = (
  orderId: number,
  itemId: number,
  reason: string,
): Promise<AxiosResponse<ReturnRequest>> =>
  api.post(`/api/v1/orders/${orderId}/items/${itemId}/returns`, { reason });

// 내 반품 목록 (페이징, 최신순)
export const getMyReturns = (page = 0, size = 20): Promise<AxiosResponse<Page<ReturnRequest>>> =>
  api.get('/api/v1/returns/me', { params: { page, size } });

// 반품 관리 목록 (ADMIN 전체 / SELLER 본인 상품 포함 건만)
export const getManagedReturns = (page = 0, size = 20): Promise<AxiosResponse<Page<ReturnRequest>>> =>
  api.get('/api/v1/returns/admin', { params: { page, size } });

// 반품 승인 (ADMIN / 해당 SELLER) — 재고 복구 + 환불 처리
export const approveReturn = (returnId: number): Promise<AxiosResponse<ReturnRequest>> =>
  api.patch(`/api/v1/returns/${returnId}/approve`);

// 반품 거부 (ADMIN / 해당 SELLER) — 거부 사유 필수
export const rejectReturn = (
  returnId: number,
  rejectReason: string,
): Promise<AxiosResponse<ReturnRequest>> =>
  api.patch(`/api/v1/returns/${returnId}/reject`, { rejectReason });
