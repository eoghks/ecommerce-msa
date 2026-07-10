import api from './axios';

export const getCategories = () =>
  api.get('/api/v1/categories');

export const createCategory = (data) =>
  api.post('/api/v1/categories', data);

export const updateCategory = (id, data) =>
  api.put(`/api/v1/categories/${id}`, data);

export const deleteCategory = (id) =>
  api.delete(`/api/v1/categories/${id}`);

export const getProducts = (params) =>
  api.get('/api/v1/products', { params });

export const getProductSuggestions = (keyword, limit = 10) =>
  api.get('/api/v1/products/suggestions', { params: { keyword, limit } });

export const getProduct = (id) =>
  api.get(`/api/v1/products/${id}`);

export const createProduct = (data) =>
  api.post('/api/v1/products', data);

export const updateProduct = (id, data) =>
  api.put(`/api/v1/products/${id}`, data);

export const deleteProduct = (id) =>
  api.delete(`/api/v1/products/${id}`);

export const getMyProducts = (params) =>
  api.get('/api/v1/products/mine', { params });

export const banProduct = (id) =>
  api.patch(`/api/v1/products/${id}/ban`);

export const unbanProduct = (id) =>
  api.patch(`/api/v1/products/${id}/unban`);

export const uploadProductImage = (file) => {
  const formData = new FormData();
  formData.append('file', file);
  return api.post('/api/v1/products/upload-image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

// ── 상품 리뷰·별점 (V1.1-1) ──────────────────────────────────

// 리뷰 목록 (공개, 최신순 페이징)
export const getReviews = (productId, page = 0, size = 10) =>
  api.get(`/api/v1/products/${productId}/reviews`, { params: { page, size } });

// 리뷰 작성 (구매자, 1인 1리뷰)
export const createReview = (productId, { rating, content }) =>
  api.post(`/api/v1/products/${productId}/reviews`, { rating, content });

// 리뷰 수정 (본인)
export const updateReview = (productId, reviewId, { rating, content }) =>
  api.put(`/api/v1/products/${productId}/reviews/${reviewId}`, { rating, content });

// 리뷰 삭제 (본인 또는 ADMIN)
export const deleteReview = (productId, reviewId) =>
  api.delete(`/api/v1/products/${productId}/reviews/${reviewId}`);
