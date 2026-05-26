import { BrowserRouter, Routes, Route } from 'react-router-dom';
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

const App = () => (
  <BrowserRouter>
    <Navbar />
    <main style={{ maxWidth: 1200, margin: '0 auto', padding: '24px 16px' }}>
      <Routes>
        {/* 공개 */}
        <Route path="/"             element={<ProductListPage />} />
        <Route path="/login"        element={<LoginPage />} />
        <Route path="/register"     element={<RegisterPage />} />
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
  </BrowserRouter>
);

export default App;
