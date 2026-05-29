import { create } from 'zustand';
import {
  getCart,
  addCartItem,
  updateCartItem,
  removeCartItem,
  clearCartApi,
} from '../api/cart';

const useCartStore = create((set, get) => ({
  items: [],
  loading: false,

  /** 서버에서 장바구니 로드 (로그인/비로그인 공통) */
  fetchCart: async () => {
    set({ loading: true });
    try {
      const res = await getCart();
      set({ items: res.data.items ?? [] });
    } catch {
      // 네트워크 오류 등 — 빈 배열 유지
    } finally {
      set({ loading: false });
    }
  },

  /** 상품 추가 → 서버 반영 후 재조회 (가격·상품명은 서버에서 Product Service 조회) */
  addItem: async (product, quantity = 1) => {
    await addCartItem({
      productId: product.id,
      quantity,
    });
    await get().fetchCart();
  },

  /** 수량 변경 → 로컬 즉시 반영 + 서버 동기화 (HR-02: 실패 시 원복) */
  updateQuantity: async (productId, quantity) => {
    const prev = get().items;
    set({ items: prev.map((i) => (i.productId === productId ? { ...i, quantity } : i)) });
    try {
      await updateCartItem(productId, quantity);
    } catch {
      set({ items: prev }); // 서버 오류 시 변경 전 상태로 원복
    }
  },

  /** 개별 삭제 → 로컬 즉시 반영 + 서버 동기화 (HR-02: 실패 시 원복) */
  removeItem: async (productId) => {
    const prev = get().items;
    set({ items: prev.filter((i) => i.productId !== productId) });
    try {
      await removeCartItem(productId);
    } catch {
      set({ items: prev }); // 서버 오류 시 삭제 취소
    }
  },

  /** 전체 비우기 */
  clear: async () => {
    await clearCartApi();
    set({ items: [] });
  },

  totalPrice: () => get().items.reduce((sum, i) => sum + i.price * i.quantity, 0),

  totalCount: () => get().items.reduce((sum, i) => sum + i.quantity, 0),
}));

export default useCartStore;
