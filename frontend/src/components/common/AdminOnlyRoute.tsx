import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import useAuthStore from '../../store/authStore';

// ADMIN 전용 라우트 — SELLER 포함 그 외 권한은 홈으로 리다이렉트
// (UI-ROLE-004: 카테고리·실패주문·모니터링 등 관리자 전용 화면 진입 차단)
const AdminOnlyRoute = ({ children }: { children: ReactNode }) => {
  const { isAuthenticated, role } = useAuthStore();
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (role !== 'ADMIN') return <Navigate to="/" replace />;
  return children;
};

export default AdminOnlyRoute;
