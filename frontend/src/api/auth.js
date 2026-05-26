import api from './axios';

export const login = (email, password) =>
  api.post('/api/v1/auth/login', { email, password });

export const register = (email, password, name) =>
  api.post('/api/v1/auth/signup', { email, password, name });

export const logout = () =>
  api.post('/api/v1/auth/logout');

export const checkEmail = (email) =>
  api.get(`/api/v1/auth/check-email?email=${encodeURIComponent(email)}`);

export const changePassword = (email, currentPassword, newPassword) =>
  api.post('/api/v1/auth/change-password', { email, currentPassword, newPassword });

export const getMe = () =>
  api.get('/api/v1/auth/me');
