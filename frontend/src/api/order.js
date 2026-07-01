import api from './axios';

export const createOrder = (items, { receiver, phone, address }) =>
  api.post('/api/v1/orders', { items, receiver, phone, address });

export const getMyOrders = (page = 0, size = 20) =>
  api.get('/api/v1/orders/me', { params: { page, size } });

export const getOrder = (id) =>
  api.get(`/api/v1/orders/${id}`);

export const cancelOrder = (id) =>
  api.delete(`/api/v1/orders/${id}`);

// 전체 주문 조회 (ADMIN)
export const getAllOrders = (page = 0, size = 20) =>
  api.get('/api/v1/orders/admin', { params: { page, size } });

// 판매자 주문 조회 (SELLER) — 본인 상품 항목만
export const getSellerOrders = (page = 0, size = 20) =>
  api.get('/api/v1/orders/seller', { params: { page, size } });

// 주문 항목 취소 (ADMIN 전체 / SELLER 본인 항목) — 사유 필수
export const cancelOrderItem = (orderId, itemId, reason) =>
  api.patch(`/api/v1/orders/${orderId}/items/${itemId}/cancel`, { reason });
