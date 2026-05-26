import { Navigate } from 'react-router-dom';
import useAuthStore from '../../store/authStore';

// ADMIN 전용 라우트 — 권한 없으면 홈으로 리다이렉트
const AdminRoute = ({ children }) => {
  const user = useAuthStore((s) => s.user);
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== 'ADMIN') return <Navigate to="/" replace />;
  return children;
};

export default AdminRoute;
