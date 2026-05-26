import { Link, useNavigate } from 'react-router-dom';
import useAuthStore from '../../store/authStore';
import useCartStore from '../../store/cartStore';
import { logout } from '../../api/auth';

const Navbar = () => {
  const navigate = useNavigate();
  const { isAuthenticated, user, logout: clearAuth } = useAuthStore();
  const totalCount = useCartStore((s) => s.totalCount());

  const handleLogout = async () => {
    try { await logout(); } catch (_) { /* 서버 오류 무시 */ }
    clearAuth();
    navigate('/login');
  };

  return (
    <nav style={{ padding: '0 24px', height: 56, display: 'flex', alignItems: 'center', gap: 24, borderBottom: '1px solid #e5e7eb' }}>
      <Link to="/" style={{ fontWeight: 700, fontSize: 18 }}>🛒 Shop</Link>
      <Link to="/products">상품</Link>

      {isAuthenticated ? (
        <>
          <Link to="/cart">장바구니 {totalCount > 0 && `(${totalCount})`}</Link>
          <Link to="/orders">내 주문</Link>
          {user?.role === 'ADMIN' && <Link to="/admin">관리자</Link>}
          <button onClick={handleLogout} style={{ marginLeft: 'auto' }}>로그아웃</button>
        </>
      ) : (
        <>
          <Link to="/login" style={{ marginLeft: 'auto' }}>로그인</Link>
          <Link to="/register">회원가입</Link>
        </>
      )}
    </nav>
  );
};

export default Navbar;
