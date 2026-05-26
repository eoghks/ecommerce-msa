import api from './axios';

export const login = (email, password) =>
  api.post('/api/v1/auth/login', { email, password });

export const register = (email, password, name) =>
  api.post('/api/v1/auth/register', { email, password, name });

export const logout = () =>
  api.post('/api/v1/auth/logout');
