import api from './axios';

export const getCart = () => api.get('/api/v1/cart');

export const addCartItem = (item) => api.post('/api/v1/cart/items', item);

export const updateCartItem = (productId, quantity) =>
  api.patch(`/api/v1/cart/items/${productId}`, { quantity });

export const removeCartItem = (productId) =>
  api.delete(`/api/v1/cart/items/${productId}`);

export const clearCartApi = () => api.delete('/api/v1/cart');

export const mergeGuestCart = () => api.post('/api/v1/cart/merge');
