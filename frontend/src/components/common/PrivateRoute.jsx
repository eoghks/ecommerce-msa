import { Navigate } from 'react-router-dom';
import useAuthStore from '../../store/authStore';

// 인증 필요 라우트 — 미로그인 시 /login 리다이렉트
const PrivateRoute = ({ children }) => {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  return isAuthenticated ? children : <Navigate to="/login" replace />;
};

export default PrivateRoute;
