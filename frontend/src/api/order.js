import api from './axios';

export const createOrder = (items, { receiver, phone, address }) =>
  api.post('/api/v1/orders', { items, receiver, phone, address });

export const getMyOrders = (page = 0, size = 20) =>
  api.get('/api/v1/orders/me', { params: { page, size } });

export const getOrder = (id) =>
  api.get(`/api/v1/orders/${id}`);

// 주문 취소 — 사유 선택(미입력 시 서버 기본 "고객 주문 취소"). 차감된 주문은 재고 복구 (M-N3)
export const cancelOrder = (id, reason) =>
  api.delete(`/api/v1/orders/${id}`, { data: reason ? { reason } : {} });

// 전체 주문 조회 (ADMIN)
export const getAllOrders = (page = 0, size = 20) =>
  api.get('/api/v1/orders/admin', { params: { page, size } });

// 실패(자동취소) 주문 조회 (ADMIN) — M-3
export const getFailedOrders = (page = 0, size = 20) =>
  api.get('/api/v1/orders/admin/failed', { params: { page, size } });

// 판매자 주문 조회 (SELLER) — 본인 상품 항목만
export const getSellerOrders = (page = 0, size = 20) =>
  api.get('/api/v1/orders/seller', { params: { page, size } });

// 주문 항목 취소 (ADMIN 전체 / SELLER 본인 항목) — 사유 필수
export const cancelOrderItem = (orderId, itemId, reason) =>
  api.patch(`/api/v1/orders/${orderId}/items/${itemId}/cancel`, { reason });
