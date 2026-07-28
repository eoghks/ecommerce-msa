import type { AxiosResponse } from 'axios';
import api from './axios';
import type {
  DeliveryStatus,
  FailedOrder,
  Order,
  OrderAddressPayload,
  OrderRequestItem,
  Page,
} from '../types';

// V1.1-3: addressId 선택 시 저장된 배송지 스냅샷 사용, 없으면 직접입력(receiver/phone/address)
export const createOrder = (
  items: OrderRequestItem[],
  { addressId, receiver, phone, address }: OrderAddressPayload,
): Promise<AxiosResponse<Order>> =>
  api.post('/api/v1/orders', { items, addressId, receiver, phone, address });

export const getMyOrders = (page = 0, size = 20): Promise<AxiosResponse<Page<Order>>> =>
  api.get('/api/v1/orders/me', { params: { page, size } });

export const getOrder = (id: number): Promise<AxiosResponse<Order>> =>
  api.get(`/api/v1/orders/${id}`);

// 주문 취소 — 사유 선택(미입력 시 서버 기본 "고객 주문 취소"). 차감된 주문은 재고 복구 (M-N3)
export const cancelOrder = (id: number, reason?: string): Promise<AxiosResponse<void>> =>
  api.delete(`/api/v1/orders/${id}`, { data: reason ? { reason } : {} });

// 전체 주문 조회 (ADMIN)
export const getAllOrders = (page = 0, size = 20): Promise<AxiosResponse<Page<Order>>> =>
  api.get('/api/v1/orders/admin', { params: { page, size } });

// 실패(자동취소) 주문 조회 (ADMIN) — M-3
export const getFailedOrders = (page = 0, size = 20): Promise<AxiosResponse<Page<FailedOrder>>> =>
  api.get('/api/v1/orders/admin/failed', { params: { page, size } });

// 판매자 주문 조회 (SELLER) — 본인 상품 항목만
export const getSellerOrders = (page = 0, size = 20): Promise<AxiosResponse<Page<Order>>> =>
  api.get('/api/v1/orders/seller', { params: { page, size } });

// 주문 항목 취소 (ADMIN 전체 / SELLER 본인 항목) — 사유 필수
export const cancelOrderItem = (orderId: number, itemId: number, reason: string): Promise<AxiosResponse<void>> =>
  api.patch(`/api/v1/orders/${orderId}/items/${itemId}/cancel`, { reason });

// V1.1-3: 배송상태 변경 (ADMIN 전체 / SELLER 본인 상품 포함 주문) — PREPARING→SHIPPING→DELIVERED
export const updateDeliveryStatus = (orderId: number, status: DeliveryStatus): Promise<AxiosResponse<void>> =>
  api.patch(`/api/v1/orders/${orderId}/delivery-status`, { status });
