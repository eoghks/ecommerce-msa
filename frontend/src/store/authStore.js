import { create } from 'zustand';

// JWT payload 디코딩 (검증 없이 클레임만 파싱)
const decodeJwt = (token) => {
  try {
    return JSON.parse(atob(token.split('.')[1]));
  } catch {
    return null;
  }
};

const initAuth = () => {
  const token = localStorage.getItem('accessToken');
  if (!token) return { isAuthenticated: false, userId: null, role: null };
  const claims = decodeJwt(token);
  if (!claims) return { isAuthenticated: false, userId: null, role: null };
  return {
    isAuthenticated: true,
    userId: claims.sub,
    role: claims.role,
  };
};

const useAuthStore = create((set) => ({
  ...initAuth(),

  login: (token) => {
    localStorage.setItem('accessToken', token);
    const claims = decodeJwt(token);
    set({
      isAuthenticated: true,
      userId: claims?.sub ?? null,
      role: claims?.role ?? null,
    });
  },

  logout: () => {
    localStorage.removeItem('accessToken');
    set({ isAuthenticated: false, userId: null, role: null });
  },
}));

export default useAuthStore;
