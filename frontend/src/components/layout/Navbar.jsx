import { useState, useRef, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import useAuthStore from '../../store/authStore';
import useCartStore from '../../store/cartStore';
import { logout } from '../../api/auth';

const Navbar = () => {
  const navigate = useNavigate();
  const { isAuthenticated, role, logout: clearAuth } = useAuthStore();
  const totalCount = useCartStore((s) => s.totalCount());
  const [myOpen, setMyOpen] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const myRef = useRef(null);
  const mobileRef = useRef(null);

  // 드롭다운/모바일 메뉴 외부 클릭 시 닫기
  useEffect(() => {
    const handler = (e) => {
      if (myRef.current && !myRef.current.contains(e.target)) setMyOpen(false);
      if (mobileRef.current && !mobileRef.current.contains(e.target)) setMobileOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  // 라우트 이동 시 모바일 메뉴 닫기
  const closeMobile = () => setMobileOpen(false);

  const handleLogout = async () => {
    try { await logout(); } catch (_) { /* 서버 오류 무시 */ }
    clearAuth();
    closeMobile();
    navigate('/login');
  };

  return (
    <nav className="sticky top-0 z-[100] bg-white border-b border-gray-200 shadow-[0_1px_4px_rgba(0,0,0,0.06)]">
      <div className="h-[60px] px-4 md:px-8 flex items-center gap-2">

        {/* 로고 */}
        <Link to="/" className="flex items-center gap-2 no-underline mr-2 md:mr-4 shrink-0">
          <span className="text-xl">🛒</span>
          <span className="text-[18px] font-bold text-brand-600" style={{ letterSpacing: '-0.3px' }}>ShopMSA</span>
        </Link>

        {/* 좌측 메뉴 — md 이상에서만 표시 */}
        <div className="hidden md:flex items-center gap-1 flex-1">
          <Link to="/products" className="px-3 py-1.5 rounded-lg text-sm font-medium text-gray-700 no-underline hover:bg-gray-100 transition-colors">
            상품
          </Link>
          {isAuthenticated && role === 'ADMIN' && (
            <Link to="/admin" className="px-3 py-1.5 rounded-lg text-sm font-medium text-amber-500 no-underline hover:bg-amber-50 transition-colors">
              관리자
            </Link>
          )}
        </div>

        {/* 우측 영역 */}
        <div className="flex items-center gap-1.5 md:gap-2 ml-auto">
          {isAuthenticated ? (
            <>
              {/* 장바구니 */}
              <Link to="/cart" className="relative flex items-center justify-center w-[38px] h-[38px] rounded-lg no-underline hover:bg-gray-100 transition-colors">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#374151" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/>
                  <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"/>
                </svg>
                {totalCount > 0 && (
                  <span className="absolute top-0.5 right-0.5 min-w-[16px] h-4 px-[3px] bg-brand-600 text-white text-[10px] font-bold rounded-lg flex items-center justify-center">
                    {totalCount}
                  </span>
                )}
              </Link>

              {/* MY 드롭다운 — md 이상 */}
              <div ref={myRef} className="relative hidden md:block">
                <button
                  onClick={() => setMyOpen((v) => !v)}
                  className="h-[34px] px-3 flex items-center text-[13px] font-medium rounded-lg transition-colors duration-150"
                  style={{
                    border: '1.5px solid #e5e7eb',
                    background: myOpen ? '#f5f3ff' : 'transparent',
                    color: myOpen ? '#4f46e5' : '#374151',
                  }}
                >
                  MY
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"
                    className="ml-1 transition-transform duration-200"
                    style={{ transform: myOpen ? 'rotate(180deg)' : 'rotate(0deg)' }}>
                    <polyline points="6 9 12 15 18 9"/>
                  </svg>
                </button>
                {myOpen && (
                  <div className="absolute top-[calc(100%+8px)] right-0 min-w-[160px] bg-white rounded-xl overflow-hidden z-[200]"
                    style={{ border: '1px solid #e5e7eb', boxShadow: '0 8px 24px rgba(0,0,0,0.10)' }}>
                    <Link to="/my/profile"
                      className="flex items-center gap-2.5 px-4 py-2.5 text-[13px] font-medium text-gray-700 no-underline hover:bg-gray-50 transition-colors"
                      onClick={() => setMyOpen(false)}>
                      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
                      </svg>
                      내 정보
                    </Link>
                    <Link to="/orders"
                      className="flex items-center gap-2.5 px-4 py-2.5 text-[13px] font-medium text-gray-700 no-underline hover:bg-gray-50 transition-colors"
                      onClick={() => setMyOpen(false)}>
                      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>
                      </svg>
                      내 주문
                    </Link>
                  </div>
                )}
              </div>

              {/* 로그아웃 — md 이상 */}
              <button onClick={handleLogout} className="btn-outline hidden md:block">로그아웃</button>

              {/* 햄버거 — md 미만 */}
              <div ref={mobileRef} className="relative md:hidden">
                <button
                  onClick={() => setMobileOpen((v) => !v)}
                  className="flex items-center justify-center w-[38px] h-[38px] rounded-lg hover:bg-gray-100 transition-colors"
                  style={{ border: '1.5px solid #e5e7eb' }}
                >
                  {mobileOpen ? (
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#374151" strokeWidth="2.5" strokeLinecap="round">
                      <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                    </svg>
                  ) : (
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#374151" strokeWidth="2.5" strokeLinecap="round">
                      <line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/>
                    </svg>
                  )}
                </button>

                {/* 모바일 드롭다운 메뉴 */}
                {mobileOpen && (
                  <div className="absolute top-[calc(100%+8px)] right-0 w-[200px] bg-white rounded-xl overflow-hidden z-[200]"
                    style={{ border: '1px solid #e5e7eb', boxShadow: '0 8px 24px rgba(0,0,0,0.10)' }}>
                    <Link to="/products" className="flex items-center px-4 py-3 text-[13px] font-medium text-gray-700 no-underline hover:bg-gray-50 transition-colors" onClick={closeMobile}>
                      상품
                    </Link>
                    {role === 'ADMIN' && (
                      <Link to="/admin" className="flex items-center px-4 py-3 text-[13px] font-medium text-amber-500 no-underline hover:bg-amber-50 transition-colors" onClick={closeMobile}>
                        관리자
                      </Link>
                    )}
                    <div className="h-px bg-gray-100 mx-3" />
                    <Link to="/my/profile" className="flex items-center gap-2.5 px-4 py-3 text-[13px] font-medium text-gray-700 no-underline hover:bg-gray-50 transition-colors" onClick={closeMobile}>
                      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
                      </svg>
                      내 정보
                    </Link>
                    <Link to="/orders" className="flex items-center gap-2.5 px-4 py-3 text-[13px] font-medium text-gray-700 no-underline hover:bg-gray-50 transition-colors" onClick={closeMobile}>
                      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>
                      </svg>
                      내 주문
                    </Link>
                    <div className="h-px bg-gray-100 mx-3" />
                    <button onClick={handleLogout} className="w-full text-left px-4 py-3 text-[13px] font-medium text-red-500 hover:bg-red-50 transition-colors bg-transparent border-none">
                      로그아웃
                    </button>
                  </div>
                )}
              </div>
            </>
          ) : (
            <>
              <Link to="/login" className="btn-brand-outline">로그인</Link>
              <Link to="/register" className="btn-brand-fill hidden sm:inline-flex">회원가입</Link>
            </>
          )}
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
