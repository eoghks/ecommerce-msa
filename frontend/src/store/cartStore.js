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

  /** 상품 추가 → 서버 반영 후 재조회 */
  addItem: async (product, quantity = 1) => {
    await addCartItem({
      productId: product.id,
      productName: product.name,
      price: product.price,
      quantity,
      imageUrl: product.imageUrl ?? null,
    });
    await get().fetchCart();
  },

  /** 수량 변경 → 로컬 즉시 반영 + 서버 동기화 */
  updateQuantity: async (productId, quantity) => {
    set({ items: get().items.map((i) => (i.productId === productId ? { ...i, quantity } : i)) });
    await updateCartItem(productId, quantity);
  },

  /** 개별 삭제 → 로컬 즉시 반영 + 서버 동기화 */
  removeItem: async (productId) => {
    set({ items: get().items.filter((i) => i.productId !== productId) });
    await removeCartItem(productId);
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
