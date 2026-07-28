import { create } from 'zustand';
import { addWishlist, removeWishlist, getMyWishlistIds } from '../api/wishlist';

interface WishlistState {
  ids: Set<number>;
  loaded: boolean;
  fetchIds: () => Promise<void>;
  isWished: (productId: number) => boolean;
  add: (productId: number) => Promise<void>;
  remove: (productId: number) => Promise<void>;
  reset: () => void;
}

/**
 * 찜 상태 전역 스토어 — 하트 표시용 상품 ID 집합을 보관.
 * 카드/상세 진입 시 fetchIds()로 일괄 조회하고, 토글은 낙관적 업데이트한다.
 */
const useWishlistStore = create<WishlistState>()((set, get) => ({
  ids: new Set<number>(),
  loaded: false,

  /** 로그인 사용자의 찜 ID 집합 로드 */
  fetchIds: async () => {
    try {
      const res = await getMyWishlistIds();
      set({ ids: new Set(res.data ?? []), loaded: true });
    } catch {
      // 비로그인/오류 — 빈 집합 유지
      set({ ids: new Set<number>(), loaded: true });
    }
  },

  /** 찜 여부 */
  isWished: (productId) => get().ids.has(productId),

  /** 찜 추가 — 낙관적 업데이트(실패 시 원복) */
  add: async (productId) => {
    const prev = get().ids;
    const next = new Set(prev);
    next.add(productId);
    set({ ids: next });
    try {
      await addWishlist(productId);
    } catch (err) {
      set({ ids: prev });
      throw err;
    }
  },

  /** 찜 해제 — 낙관적 업데이트(실패 시 원복) */
  remove: async (productId) => {
    const prev = get().ids;
    const next = new Set(prev);
    next.delete(productId);
    set({ ids: next });
    try {
      await removeWishlist(productId);
    } catch (err) {
      set({ ids: prev });
      throw err;
    }
  },

  /** 로그아웃 등에서 초기화 */
  reset: () => set({ ids: new Set<number>(), loaded: false }),
}));

export default useWishlistStore;
