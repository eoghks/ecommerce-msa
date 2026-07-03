import api from './axios';

// 찜 추가(멱등)
export const addWishlist = (productId) =>
  api.post(`/api/v1/wishlist/${productId}`);

// 찜 해제(멱등)
export const removeWishlist = (productId) =>
  api.delete(`/api/v1/wishlist/${productId}`);

// 내 찜 목록(페이징)
export const getMyWishlist = (params) =>
  api.get('/api/v1/wishlist/me', { params });

// 내 찜 상품 ID 집합(하트 표시용)
export const getMyWishlistIds = () =>
  api.get('/api/v1/wishlist/me/ids');
