import type { AxiosResponse } from 'axios';
import api from './axios';
import type {
  Category,
  Page,
  Product,
  ProductImageUploadResponse,
  ProductPayload,
  Review,
  ReviewInput,
} from '../types';

export const getCategories = (): Promise<AxiosResponse<Category[]>> =>
  api.get('/api/v1/categories');

export const createCategory = (data: { name: string }): Promise<AxiosResponse<Category>> =>
  api.post('/api/v1/categories', data);

export const updateCategory = (id: number, data: { name: string }): Promise<AxiosResponse<Category>> =>
  api.put(`/api/v1/categories/${id}`, data);

export const deleteCategory = (id: number): Promise<AxiosResponse<void>> =>
  api.delete(`/api/v1/categories/${id}`);

export const getProducts = (params?: Record<string, unknown>): Promise<AxiosResponse<Page<Product>>> =>
  api.get('/api/v1/products', { params });

export const getProductSuggestions = (keyword: string, limit = 10): Promise<AxiosResponse<string[]>> =>
  api.get('/api/v1/products/suggestions', { params: { keyword, limit } });

export const getProduct = (id: number | string): Promise<AxiosResponse<Product>> =>
  api.get(`/api/v1/products/${id}`);

export const createProduct = (data: ProductPayload): Promise<AxiosResponse<Product>> =>
  api.post('/api/v1/products', data);

export const updateProduct = (id: number, data: ProductPayload): Promise<AxiosResponse<Product>> =>
  api.put(`/api/v1/products/${id}`, data);

export const deleteProduct = (id: number): Promise<AxiosResponse<void>> =>
  api.delete(`/api/v1/products/${id}`);

export const getMyProducts = (params?: Record<string, unknown>): Promise<AxiosResponse<Page<Product>>> =>
  api.get('/api/v1/products/mine', { params });

export const banProduct = (id: number): Promise<AxiosResponse<void>> =>
  api.patch(`/api/v1/products/${id}/ban`);

export const unbanProduct = (id: number): Promise<AxiosResponse<void>> =>
  api.patch(`/api/v1/products/${id}/unban`);

export const uploadProductImage = (file: File): Promise<AxiosResponse<ProductImageUploadResponse>> => {
  const formData = new FormData();
  formData.append('file', file);
  return api.post('/api/v1/products/upload-image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

// ── 상품 리뷰·별점 (V1.1-1) ──────────────────────────────────

// 리뷰 목록 (공개, 최신순 페이징)
export const getReviews = (productId: number | string, page = 0, size = 10): Promise<AxiosResponse<Page<Review>>> =>
  api.get(`/api/v1/products/${productId}/reviews`, { params: { page, size } });

// 리뷰 작성 (구매자, 1인 1리뷰)
export const createReview = (productId: number | string, { rating, content }: ReviewInput): Promise<AxiosResponse<Review>> =>
  api.post(`/api/v1/products/${productId}/reviews`, { rating, content });

// 리뷰 수정 (본인)
export const updateReview = (productId: number | string, reviewId: number, { rating, content }: ReviewInput): Promise<AxiosResponse<Review>> =>
  api.put(`/api/v1/products/${productId}/reviews/${reviewId}`, { rating, content });

// 리뷰 삭제 (본인 또는 ADMIN)
export const deleteReview = (productId: number | string, reviewId: number): Promise<AxiosResponse<void>> =>
  api.delete(`/api/v1/products/${productId}/reviews/${reviewId}`);
