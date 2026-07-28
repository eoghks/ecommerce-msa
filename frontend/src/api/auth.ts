import type { AxiosResponse } from 'axios';
import api from './axios';
import type {
  AuthTokenResponse,
  CheckEmailResponse,
  ForgotPasswordResponse,
  SellerApplyResponse,
  UserProfile,
} from '../types';

export const login = (email: string, password: string): Promise<AxiosResponse<AuthTokenResponse>> =>
  api.post('/api/v1/auth/login', { email, password });

export const register = (email: string, password: string, name: string): Promise<AxiosResponse<void>> =>
  api.post('/api/v1/auth/signup', { email, password, name });

export const logout = (): Promise<AxiosResponse<void>> =>
  api.post('/api/v1/auth/logout');

export const checkEmail = (email: string): Promise<AxiosResponse<CheckEmailResponse>> =>
  api.get(`/api/v1/auth/check-email?email=${encodeURIComponent(email)}`);

export const changePassword = (email: string, currentPassword: string, newPassword: string): Promise<AxiosResponse<void>> =>
  api.post('/api/v1/auth/change-password', { email, currentPassword, newPassword });

export const getMe = (): Promise<AxiosResponse<UserProfile>> =>
  api.get('/api/v1/auth/me');

export const forgotPassword = (email: string): Promise<AxiosResponse<ForgotPasswordResponse>> =>
  api.post('/api/v1/auth/forgot-password', { email });

export const applyForSeller = (phone: string): Promise<AxiosResponse<SellerApplyResponse>> =>
  api.post('/api/v1/auth/seller/apply', { phone });
