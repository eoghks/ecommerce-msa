import api from './axios';

export const getCategories = () =>
  api.get('/api/v1/categories');

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
