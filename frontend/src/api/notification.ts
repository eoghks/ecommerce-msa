import type { AxiosResponse } from 'axios';
import api from './axios';
import type { Notification, Page, UnreadCountResponse } from '../types';

// 내 알림 목록 (최신순 페이징)
export const getMyNotifications = (page = 0, size = 20): Promise<AxiosResponse<Page<Notification>>> =>
  api.get('/api/v1/notifications/me', { params: { page, size } });

// 미읽음 개수 (뱃지용)
export const getUnreadCount = (): Promise<AxiosResponse<UnreadCountResponse>> =>
  api.get('/api/v1/notifications/me/unread-count');

// 단건 읽음 (본인)
export const markNotificationRead = (id: number): Promise<AxiosResponse<void>> =>
  api.patch(`/api/v1/notifications/${id}/read`);

// 전체 읽음 (본인)
export const markAllNotificationsRead = (): Promise<AxiosResponse<void>> =>
  api.patch('/api/v1/notifications/read-all');
