import { useEffect } from 'react';
import { BrowserRouter, Routes, Route, useLocation } from 'react-router-dom';
import Navbar from './components/layout/Navbar';
import PrivateRoute from './components/common/PrivateRoute';
import AdminRoute from './components/common/AdminRoute';
import api from './api/axios';
import useCartStore from './store/cartStore';
import useAuthStore from './store/authStore';

import LoginPage from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';
import ForgotPasswordPage from './pages/auth/ForgotPasswordPage';
import ProductListPage from './pages/product/ProductListPage';
import ProductDetailPage from './pages/product/ProductDetailPage';
import CartPage from './pages/order/CartPage';
import OrderPage from './pages/order/OrderPage';
import OrderListPage from './pages/order/OrderListPage';
import AdminProductPage from './pages/admin/AdminProductPage';
import AdminCategoryPage from './pages/admin/AdminCategoryPage';
import AdminOrderPage from './pages/admin/AdminOrderPage';
import AdminFailedOrderPage from './pages/admin/AdminFailedOrderPage';
import AdminMonitoringPage from './pages/admin/AdminMonitoringPage';
import MyProfilePage from './pages/my/MyProfilePage';
import WishlistPage from './pages/my/WishlistPage';

// 인증 페이지에서는 Navbar 숨김
const AUTH_PATHS = ['/login', '/register', '/forgot-password'];

const Layout = ({ children }) => {
  const { pathname } = useLocation();
  const isAuthPage = AUTH_PATHS.includes(pathname);
  const fetchCart = useCartStore((s) => s.fetchCart);

  useEffect(() => {
    // CR-06: guestId HttpOnly 쿠키를 서버가 발급 — JS에서 직접 쿠키 생성 금지
    api.post('/api/v1/cart/guest/init').catch(() => {/* 실패해도 비로그인 장바구니 미사용으로 처리 */});
    // 앱 진입 시 장바구니 서버 동기화
    fetchCart();
  }, []);

  return (
    <>
      {!isAuthPage && <Navbar />}
      {children}
    </>
  );
};

// 초기 세션 복원 중 표시할 풀스크린 로더
const BootLoader = () => (
  <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
    <div className="w-8 h-8 rounded-full animate-spin border-[3px] border-gray-200 border-t-brand-600" />
  </div>
);

const App = () => {
  const tryRestoreAuth = useAuthStore((s) => s.tryRestoreAuth);
  const authResolved = useAuthStore((s) => s.authResolved);

  useEffect(() => {
    // 앱 시작/새 탭 진입 시 refresh 쿠키로 세션 복원 시도 (1회)
    tryRestoreAuth();
  }, []);

  // 복원 완료 전엔 로더 — PrivateRoute가 미인증으로 오판해 /login 튕기는 것 방지
  if (!authResolved) return <BootLoader />;

  return (
  <BrowserRouter>
    <Layout>
      <Routes>
        {/* 인증 페이지 — 풀 페이지, 네비바 없음 */}
        <Route path="/login"            element={<LoginPage />} />
        <Route path="/register"         element={<RegisterPage />} />
        <Route path="/forgot-password"  element={<ForgotPasswordPage />} />

        {/* 일반 페이지 — 1600px 컨테이너 */}
        <Route path="/*" element={
          <main style={{ maxWidth: 1200, margin: '0 auto', padding: '24px 20px', width: '100%' }}>
            <Routes>
              <Route path="/"             element={<ProductListPage />} />
              <Route path="/products"     element={<ProductListPage />} />
              <Route path="/products/:id" element={<ProductDetailPage />} />

              {/* 비로그인도 접근 가능 (게스트 장바구니) */}
              <Route path="/cart"         element={<CartPage />} />
              <Route path="/order"        element={<PrivateRoute><OrderPage /></PrivateRoute>} />
              <Route path="/orders"       element={<PrivateRoute><OrderListPage /></PrivateRoute>} />
              <Route path="/my/profile"   element={<PrivateRoute><MyProfilePage /></PrivateRoute>} />
              <Route path="/my/wishlist"  element={<PrivateRoute><WishlistPage /></PrivateRoute>} />

              {/* ADMIN 전용 */}
              <Route path="/admin"        element={<AdminRoute><AdminProductPage /></AdminRoute>} />
              <Route path="/admin/categories" element={<AdminRoute><AdminCategoryPage /></AdminRoute>} />
              <Route path="/admin/orders" element={<AdminRoute><AdminOrderPage /></AdminRoute>} />
              <Route path="/admin/failed" element={<AdminRoute><AdminFailedOrderPage /></AdminRoute>} />
              <Route path="/admin/monitoring" element={<AdminRoute><AdminMonitoringPage /></AdminRoute>} />
            </Routes>
          </main>
        } />
      </Routes>
    </Layout>
  </BrowserRouter>
  );
};

export default App;
