import type { AxiosResponse } from 'axios';
import api from './axios';
import type { Cart } from '../types';

export const getCart = (): Promise<AxiosResponse<Cart>> => api.get('/api/v1/cart');

export const addCartItem = (item: { productId: number; quantity: number }): Promise<AxiosResponse<void>> =>
  api.post('/api/v1/cart/items', item);

export const updateCartItem = (productId: number, quantity: number): Promise<AxiosResponse<void>> =>
  api.patch(`/api/v1/cart/items/${productId}`, { quantity });

export const removeCartItem = (productId: number): Promise<AxiosResponse<void>> =>
  api.delete(`/api/v1/cart/items/${productId}`);

export const clearCartApi = (): Promise<AxiosResponse<void>> => api.delete('/api/v1/cart');

export const mergeGuestCart = (): Promise<AxiosResponse<void>> => api.post('/api/v1/cart/merge');
