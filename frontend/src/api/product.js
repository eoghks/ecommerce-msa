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
