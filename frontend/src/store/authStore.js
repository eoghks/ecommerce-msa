import { create } from 'zustand';

const useAuthStore = create((set) => ({
  user: null,
  isAuthenticated: !!localStorage.getItem('accessToken'),

  login: (token, userInfo) => {
    localStorage.setItem('accessToken', token);
    set({ user: userInfo, isAuthenticated: true });
  },

  logout: () => {
    localStorage.removeItem('accessToken');
    set({ user: null, isAuthenticated: false });
  },
}));

export default useAuthStore;
