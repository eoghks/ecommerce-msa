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
  const myRef = useRef(null);

  // 드롭다운 외부 클릭 시 닫기
  useEffect(() => {
    const handler = (e) => {
      if (myRef.current && !myRef.current.contains(e.target)) {
        setMyOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const handleLogout = async () => {
    try { await logout(); } catch (_) { /* 서버 오류 무시 */ }
    clearAuth();
    navigate('/login');
  };

  return (
    <nav style={styles.nav}>
      {/* 로고 */}
      <Link to="/" style={styles.logo}>
        <span style={styles.logoIcon}>🛒</span>
        <span style={styles.logoText}>ShopMSA</span>
      </Link>

      {/* 좌측 메뉴 */}
      <div style={styles.menu}>
        <Link to="/products" style={styles.menuLink}>상품</Link>
        {isAuthenticated && role === 'ADMIN' && (
          <Link to="/admin" style={{ ...styles.menuLink, color: '#f59e0b' }}>관리자</Link>
        )}
      </div>

      {/* 우측 영역 */}
      <div style={styles.right}>
        {isAuthenticated ? (
          <>
            {/* 장바구니 */}
            <Link to="/cart" style={styles.iconBtn}>
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#374151" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/>
                <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"/>
              </svg>
              {totalCount > 0 && <span style={styles.badge}>{totalCount}</span>}
            </Link>

            {/* MY 드롭다운 */}
            <div ref={myRef} style={{ position: 'relative' }}>
              <button
                onClick={() => setMyOpen((v) => !v)}
                style={{ ...styles.myBtn, background: myOpen ? '#f5f3ff' : 'transparent', color: myOpen ? '#4f46e5' : '#374151' }}
              >
                MY
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"
                  style={{ marginLeft: 4, transition: 'transform 0.2s', transform: myOpen ? 'rotate(180deg)' : 'rotate(0deg)' }}>
                  <polyline points="6 9 12 15 18 9"/>
                </svg>
              </button>

              {myOpen && (
                <div style={styles.dropdown}>
                  <Link to="/my/profile" style={styles.dropItem} onClick={() => setMyOpen(false)}>
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
                    </svg>
                    내 정보
                  </Link>
                  <Link to="/orders" style={styles.dropItem} onClick={() => setMyOpen(false)}>
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>
                    </svg>
                    내 주문
                  </Link>
                </div>
              )}
            </div>

            {/* 로그아웃 */}
            <button onClick={handleLogout} style={styles.logoutBtn}>로그아웃</button>
          </>
        ) : (
          <>
            <Link to="/login" style={styles.loginBtn}>로그인</Link>
            <Link to="/register" style={styles.registerBtn}>회원가입</Link>
          </>
        )}
      </div>
    </nav>
  );
};

const styles = {
  nav: {
    position: 'sticky',
    top: 0,
    zIndex: 100,
    height: 60,
    padding: '0 32px',
    background: '#ffffff',
    borderBottom: '1px solid #e5e7eb',
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    boxShadow: '0 1px 4px rgba(0,0,0,0.06)',
  },
  logo: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    textDecoration: 'none',
    marginRight: 16,
  },
  logoIcon: { fontSize: 20 },
  logoText: { fontSize: 18, fontWeight: 700, color: '#4f46e5', letterSpacing: '-0.3px' },
  menu: { display: 'flex', alignItems: 'center', gap: 4, flex: 1 },
  menuLink: {
    padding: '6px 12px', borderRadius: 8, fontSize: 14, fontWeight: 500,
    color: '#374151', textDecoration: 'none',
  },
  right: { display: 'flex', alignItems: 'center', gap: 8, marginLeft: 'auto' },
  iconBtn: {
    position: 'relative', display: 'flex', alignItems: 'center', justifyContent: 'center',
    width: 38, height: 38, borderRadius: 8, textDecoration: 'none',
  },
  badge: {
    position: 'absolute', top: 2, right: 2,
    minWidth: 16, height: 16, padding: '0 3px',
    background: '#4f46e5', color: '#fff',
    fontSize: 10, fontWeight: 700, borderRadius: 8,
    display: 'flex', alignItems: 'center', justifyContent: 'center',
  },
  myBtn: {
    height: 34, padding: '0 12px', border: '1.5px solid #e5e7eb', borderRadius: 8,
    fontSize: 13, fontWeight: 500, cursor: 'pointer',
    display: 'flex', alignItems: 'center', transition: 'background 0.15s, color 0.15s',
  },
  dropdown: {
    position: 'absolute', top: 'calc(100% + 8px)', right: 0,
    minWidth: 160, background: '#fff',
    border: '1px solid #e5e7eb', borderRadius: 12,
    boxShadow: '0 8px 24px rgba(0,0,0,0.10)',
    overflow: 'hidden', zIndex: 200,
  },
  dropItem: {
    display: 'flex', alignItems: 'center', gap: 10,
    padding: '10px 16px', fontSize: 13, fontWeight: 500,
    color: '#374151', textDecoration: 'none',
    transition: 'background 0.12s',
  },
  dropDivider: { height: 1, background: '#f3f4f6', margin: '2px 0' },
  logoutBtn: {
    height: 34, padding: '0 14px',
    background: 'transparent', border: '1.5px solid #e5e7eb',
    borderRadius: 8, fontSize: 13, fontWeight: 500,
    color: '#374151', cursor: 'pointer',
  },
  loginBtn: {
    height: 34, padding: '0 14px',
    display: 'inline-flex', alignItems: 'center',
    border: '1.5px solid #4f46e5', borderRadius: 8,
    fontSize: 13, fontWeight: 500, color: '#4f46e5', textDecoration: 'none',
  },
  registerBtn: {
    height: 34, padding: '0 14px',
    display: 'inline-flex', alignItems: 'center',
    background: '#4f46e5', border: '1.5px solid #4f46e5', borderRadius: 8,
    fontSize: 13, fontWeight: 500, color: '#fff', textDecoration: 'none',
  },
};

export default Navbar;
