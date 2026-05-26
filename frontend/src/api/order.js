import api from './axios';

export const createOrder = (items) =>
  api.post('/api/v1/orders', { items });

export const getMyOrders = (page = 0, size = 20) =>
  api.get('/api/v1/orders/me', { params: { page, size } });

export const getOrder = (id) =>
  api.get(`/api/v1/orders/${id}`);

export const cancelOrder = (id) =>
  api.delete(`/api/v1/orders/${id}`);
