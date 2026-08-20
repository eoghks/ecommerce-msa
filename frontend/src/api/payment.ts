import type { AxiosResponse } from 'axios';
import api from './axios';
import type { Payment, PaymentConfirmPayload } from '../types';

// V1.1-6: 결제 승인 — 결제위젯 성공 후 (paymentKey, orderId, amount)로 최종 승인
// 금액은 서버가 주문의 payableAmount 와 대조하므로 불일치 시 400으로 거부된다
export const confirmPayment = (payload: PaymentConfirmPayload): Promise<AxiosResponse<Payment>> =>
  api.post('/api/v1/payments/confirm', payload);

// 주문 결제 상태 조회 — 본인 결제만(없거나 타인 것이면 404)
export const getPaymentByOrder = (orderId: number): Promise<AxiosResponse<Payment>> =>
  api.get(`/api/v1/payments/order/${orderId}`);
