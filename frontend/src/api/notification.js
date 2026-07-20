import api from './axios';

// 내 알림 목록 (최신순 페이징)
export const getMyNotifications = (page = 0, size = 20) =>
  api.get('/api/v1/notifications/me', { params: { page, size } });

// 미읽음 개수 (뱃지용)
export const getUnreadCount = () =>
  api.get('/api/v1/notifications/me/unread-count');

// 단건 읽음 (본인)
export const markNotificationRead = (id) =>
  api.patch(`/api/v1/notifications/${id}/read`);

// 전체 읽음 (본인)
export const markAllNotificationsRead = () =>
  api.patch('/api/v1/notifications/read-all');
