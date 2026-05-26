import { BrowserRouter, Routes, Route, useLocation } from 'react-router-dom';
import Navbar from './components/layout/Navbar';
import PrivateRoute from './components/common/PrivateRoute';
import AdminRoute from './components/common/AdminRoute';

import LoginPage from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';
import ProductListPage from './pages/product/ProductListPage';
import ProductDetailPage from './pages/product/ProductDetailPage';
import CartPage from './pages/order/CartPage';
import OrderPage from './pages/order/OrderPage';
import OrderListPage from './pages/order/OrderListPage';
import AdminProductPage from './pages/admin/AdminProductPage';
import AdminOrderPage from './pages/admin/AdminOrderPage';

// 인증 페이지에서는 Navbar 숨김
const AUTH_PATHS = ['/login', '/register'];

const Layout = ({ children }) => {
  const { pathname } = useLocation();
  const isAuthPage = AUTH_PATHS.includes(pathname);

  return (
    <>
      {!isAuthPage && <Navbar />}
      {children}
    </>
  );
};

const App = () => (
  <BrowserRouter>
    <Layout>
      <Routes>
        {/* 인증 페이지 — 풀 페이지, 네비바 없음 */}
        <Route path="/login"    element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        {/* 일반 페이지 — 1200px 컨테이너 */}
        <Route path="/*" element={
          <main style={{ maxWidth: 1200, margin: '0 auto', padding: '24px 16px' }}>
            <Routes>
              <Route path="/"             element={<ProductListPage />} />
              <Route path="/products"     element={<ProductListPage />} />
              <Route path="/products/:id" element={<ProductDetailPage />} />

              {/* 로그인 필요 */}
              <Route path="/cart"   element={<PrivateRoute><CartPage /></PrivateRoute>} />
              <Route path="/order"  element={<PrivateRoute><OrderPage /></PrivateRoute>} />
              <Route path="/orders" element={<PrivateRoute><OrderListPage /></PrivateRoute>} />

              {/* ADMIN 전용 */}
              <Route path="/admin"        element={<AdminRoute><AdminProductPage /></AdminRoute>} />
              <Route path="/admin/orders" element={<AdminRoute><AdminOrderPage /></AdminRoute>} />
            </Routes>
          </main>
        } />
      </Routes>
    </Layout>
  </BrowserRouter>
);

export default App;
