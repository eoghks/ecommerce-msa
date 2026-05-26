import { create } from 'zustand';

const useCartStore = create((set, get) => ({
  items: [],   // [{ productId, productName, price, quantity }]

  addItem: (product, quantity = 1) => {
    const items = get().items;
    const existing = items.find((i) => i.productId === product.id);
    if (existing) {
      set({
        items: items.map((i) =>
          i.productId === product.id
            ? { ...i, quantity: i.quantity + quantity }
            : i
        ),
      });
    } else {
      set({
        items: [
          ...items,
          { productId: product.id, productName: product.name, price: product.price, quantity },
        ],
      });
    }
  },

  removeItem: (productId) =>
    set({ items: get().items.filter((i) => i.productId !== productId) }),

  updateQuantity: (productId, quantity) =>
    set({
      items: get().items.map((i) =>
        i.productId === productId ? { ...i, quantity } : i
      ),
    }),

  clear: () => set({ items: [] }),

  totalPrice: () =>
    get().items.reduce((sum, i) => sum + i.price * i.quantity, 0),

  totalCount: () =>
    get().items.reduce((sum, i) => sum + i.quantity, 0),
}));

export default useCartStore;
