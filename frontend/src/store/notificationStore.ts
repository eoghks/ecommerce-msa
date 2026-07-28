import { create } from 'zustand';
import {
  getMyNotifications,
  getUnreadCount,
  markNotificationRead,
  markAllNotificationsRead,
} from '../api/notification';
import type { Notification } from '../types';

// 미읽음 개수 폴링 주기 (30초) — 로그인 상태에서만 동작
const POLL_INTERVAL_MS = 30_000;

interface NotificationState {
  unreadCount: number;
  items: Notification[];
  loading: boolean;
  timerId: ReturnType<typeof setInterval> | null;
  fetchUnreadCount: () => Promise<void>;
  fetchList: () => Promise<void>;
  markRead: (id: number) => Promise<void>;
  markAllRead: () => Promise<void>;
  startPolling: () => void;
  stopPolling: () => void;
  reset: () => void;
}

/**
 * 알림 전역 스토어 (V1.1-4).
 * - 미읽음 개수를 주기 폴링하여 Navbar 뱃지에 노출.
 * - 드롭다운 열 때 목록을 조회하고, 항목 클릭/모두 읽음으로 읽음 처리.
 * - 로그아웃 시 stopPolling()+reset()으로 폴링 중단·상태 초기화.
 */
const useNotificationStore = create<NotificationState>()((set, get) => ({
  unreadCount: 0,
  items: [],
  loading: false,
  timerId: null,

  /** 미읽음 개수 갱신 (뱃지) */
  fetchUnreadCount: async () => {
    try {
      const res = await getUnreadCount();
      set({ unreadCount: res.data?.count ?? 0 });
    } catch {
      // 비로그인/오류 — 뱃지 유지
    }
  },

  /** 알림 목록 조회 (드롭다운 오픈 시) */
  fetchList: async () => {
    set({ loading: true });
    try {
      const res = await getMyNotifications(0, 20);
      set({ items: res.data?.content ?? [], loading: false });
    } catch {
      set({ loading: false });
    }
  },

  /** 단건 읽음 — 낙관적 업데이트 */
  markRead: async (id) => {
    const target = get().items.find((n) => n.id === id);
    if (!target || target.isRead) return;
    set((s) => ({
      items: s.items.map((n) => (n.id === id ? { ...n, isRead: true } : n)),
      unreadCount: Math.max(0, s.unreadCount - 1),
    }));
    try {
      await markNotificationRead(id);
    } catch {
      // 실패 시 서버 값으로 재동기화
      get().fetchUnreadCount();
    }
  },

  /** 전체 읽음 */
  markAllRead: async () => {
    set((s) => ({
      items: s.items.map((n) => ({ ...n, isRead: true })),
      unreadCount: 0,
    }));
    try {
      await markAllNotificationsRead();
    } catch {
      get().fetchUnreadCount();
    }
  },

  /** 미읽음 폴링 시작 (로그인 시) — 중복 시작 방지 */
  startPolling: () => {
    if (get().timerId) return;
    get().fetchUnreadCount();
    const timerId = setInterval(() => get().fetchUnreadCount(), POLL_INTERVAL_MS);
    set({ timerId });
  },

  /** 폴링 중단 */
  stopPolling: () => {
    const { timerId } = get();
    if (timerId) clearInterval(timerId);
    set({ timerId: null });
  },

  /** 로그아웃 등에서 초기화 (폴링 중단 포함) */
  reset: () => {
    const { timerId } = get();
    if (timerId) clearInterval(timerId);
    set({ unreadCount: 0, items: [], loading: false, timerId: null });
  },
}));

export default useNotificationStore;
