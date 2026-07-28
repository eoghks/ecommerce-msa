import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import useAuthStore from '../../store/authStore';

// ADMIN / SELLER 공용 라우트 — 그 외 권한은 홈으로 리다이렉트
const AdminRoute = ({ children }: { children: ReactNode }) => {
  const { isAuthenticated, role } = useAuthStore();
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (role !== 'ADMIN' && role !== 'SELLER') return <Navigate to="/" replace />;
  return children;
};

export default AdminRoute;
