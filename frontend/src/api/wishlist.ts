import type { AxiosResponse } from 'axios';
import api from './axios';
import type { Page, WishlistItem } from '../types';

// 찜 추가(멱등)
export const addWishlist = (productId: number): Promise<AxiosResponse<void>> =>
  api.post(`/api/v1/wishlist/${productId}`);

// 찜 해제(멱등)
export const removeWishlist = (productId: number): Promise<AxiosResponse<void>> =>
  api.delete(`/api/v1/wishlist/${productId}`);

// 내 찜 목록(페이징)
export const getMyWishlist = (params?: Record<string, unknown>): Promise<AxiosResponse<Page<WishlistItem>>> =>
  api.get('/api/v1/wishlist/me', { params });

// 내 찜 상품 ID 집합(하트 표시용)
export const getMyWishlistIds = (): Promise<AxiosResponse<number[]>> =>
  api.get('/api/v1/wishlist/me/ids');
