import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { login } from '../../api/auth';
import useAuthStore from '../../store/authStore';

// 아이콘 컴포넌트
const IconEmail = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#9ca3af" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="2" y="4" width="20" height="16" rx="2" />
    <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7" />
  </svg>
);

const IconLock = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#9ca3af" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
    <path d="M7 11V7a5 5 0 0 1 10 0v4" />
  </svg>
);

const IconShop = () => (
  <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#4f46e5" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z" />
    <line x1="3" y1="6" x2="21" y2="6" />
    <path d="M16 10a4 4 0 0 1-8 0" />
  </svg>
);

const LoginPage = () => {
  const navigate = useNavigate();
  const loginStore = useAuthStore((s) => s.login);

  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // body 배경을 페이지 배경색으로 통일 — 스크롤 시 흰 배경 노출 방지
  useEffect(() => {
    document.body.style.background = '#f5f5ff';
    return () => { document.body.style.background = ''; };
  }, []);
  const [focusedField, setFocusedField] = useState('');

  const handleChange = (e) => {
    setError('');
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!form.email || !form.password) {
      setError('이메일과 비밀번호를 입력해주세요.');
      return;
    }

    setLoading(true);
    try {
      const res = await login(form.email, form.password);
      const { accessToken, user } = res.data;
      loginStore(accessToken, user);
      navigate('/products');
    } catch (err) {
      const msg = err.response?.data?.message || '이메일 또는 비밀번호가 올바르지 않습니다.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  // 입력 필드 스타일 (포커스 상태 반영)
  const inputStyle = (name) => ({
    width: '100%',
    height: 44,
    padding: '0 12px 0 40px',
    border: `1.5px solid ${focusedField === name ? '#4f46e5' : '#e5e7eb'}`,
    borderRadius: 10,
    fontSize: 14,
    color: '#111827',
    background: '#fff',
    outline: 'none',
    boxSizing: 'border-box',
    boxShadow: focusedField === name ? '0 0 0 3px rgba(79,70,229,0.12)' : 'none',
    transition: 'border-color 0.15s, box-shadow 0.15s',
  });

  return (
    <div style={styles.page}>
      {/* 배경 장식 */}
      <div style={styles.bgCircle1} />
      <div style={styles.bgCircle2} />

      <div style={styles.card}>
        {/* 로고 */}
        <div style={styles.logo}>
          <div style={styles.logoIcon}><IconShop /></div>
          <span style={styles.logoText}>ShopMSA</span>
        </div>

        <h1 style={styles.title}>다시 오셨군요!</h1>
        <p style={styles.subtitle}>계정에 로그인하세요</p>

        <form onSubmit={handleSubmit} style={styles.form}>
          {/* 이메일 */}
          <div style={styles.field}>
            <label style={styles.label}>이메일</label>
            <div style={styles.inputWrapper}>
              <span style={styles.inputIcon}><IconEmail /></span>
              <input
                type="email"
                name="email"
                value={form.email}
                onChange={handleChange}
                onFocus={() => setFocusedField('email')}
                onBlur={() => setFocusedField('')}
                placeholder="이메일을 입력하세요"
                style={inputStyle('email')}
                autoComplete="email"
              />
            </div>
          </div>

          {/* 비밀번호 */}
          <div style={styles.field}>
            <div style={styles.labelRow}>
              <label style={styles.label}>비밀번호</label>
              <span style={styles.forgotLink}>비밀번호를 잊으셨나요?</span>
            </div>
            <div style={styles.inputWrapper}>
              <span style={styles.inputIcon}><IconLock /></span>
              <input
                type="password"
                name="password"
                value={form.password}
                onChange={handleChange}
                onFocus={() => setFocusedField('password')}
                onBlur={() => setFocusedField('')}
                placeholder="비밀번호를 입력하세요"
                style={inputStyle('password')}
                autoComplete="current-password"
              />
            </div>
          </div>

          {/* 에러 메시지 */}
          {error && (
            <div style={styles.errorBox}>
              <span style={styles.errorDot}>●</span>
              {error}
            </div>
          )}

          {/* 로그인 버튼 */}
          <button
            type="submit"
            disabled={loading}
            style={{ ...styles.button, opacity: loading ? 0.7 : 1 }}
          >
            {loading ? (
              <span style={styles.loadingRow}>
                <span style={styles.spinner} />
                로그인 중...
              </span>
            ) : '로그인'}
          </button>
        </form>

        {/* 구분선 */}
        <div style={styles.divider}>
          <span style={styles.dividerLine} />
          <span style={styles.dividerText}>또는</span>
          <span style={styles.dividerLine} />
        </div>

        {/* 회원가입 링크 */}
        <p style={styles.footer}>
          아직 계정이 없으신가요?{' '}
          <Link to="/register" style={styles.link}>회원가입</Link>
        </p>
      </div>

      {/* 스피너 keyframe 주입 */}
      <style>{`
        @keyframes spin { to { transform: rotate(360deg); } }
      `}</style>
    </div>
  );
};

const styles = {
  page: {
    minHeight: '100vh',
    background: '#f5f5ff',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '24px 16px',
    position: 'relative',
  },
  bgCircle1: {
    position: 'absolute',
    width: 400,
    height: 400,
    borderRadius: '50%',
    background: 'radial-gradient(circle, rgba(79,70,229,0.12) 0%, transparent 70%)',
    top: -100,
    right: -100,
    pointerEvents: 'none',
  },
  bgCircle2: {
    position: 'absolute',
    width: 300,
    height: 300,
    borderRadius: '50%',
    background: 'radial-gradient(circle, rgba(99,102,241,0.08) 0%, transparent 70%)',
    bottom: -80,
    left: -80,
    pointerEvents: 'none',
  },
  card: {
    position: 'relative',
    width: '100%',
    maxWidth: 420,
    background: '#ffffff',
    borderRadius: 20,
    padding: '40px 36px',
    boxShadow: '0 4px 32px rgba(79,70,229,0.10), 0 1px 4px rgba(0,0,0,0.06)',
    border: '1px solid rgba(79,70,229,0.08)',
  },
  logo: {
    display: 'flex',
    alignItems: 'center',
    gap: 10,
    marginBottom: 28,
  },
  logoIcon: {
    width: 44,
    height: 44,
    background: '#eef2ff',
    borderRadius: 12,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
  logoText: {
    fontSize: 20,
    fontWeight: 700,
    color: '#4f46e5',
    letterSpacing: '-0.3px',
  },
  title: {
    margin: '0 0 6px',
    fontSize: 26,
    fontWeight: 700,
    color: '#111827',
    letterSpacing: '-0.5px',
  },
  subtitle: {
    margin: '0 0 28px',
    fontSize: 14,
    color: '#6b7280',
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: 18,
  },
  field: {
    display: 'flex',
    flexDirection: 'column',
    gap: 7,
  },
  labelRow: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  label: {
    fontSize: 13,
    fontWeight: 500,
    color: '#374151',
  },
  forgotLink: {
    fontSize: 12,
    color: '#4f46e5',
    cursor: 'pointer',
  },
  inputWrapper: {
    position: 'relative',
    display: 'flex',
    alignItems: 'center',
  },
  inputIcon: {
    position: 'absolute',
    left: 12,
    display: 'flex',
    alignItems: 'center',
    pointerEvents: 'none',
  },
  errorBox: {
    display: 'flex',
    alignItems: 'center',
    gap: 6,
    padding: '10px 14px',
    background: '#fef2f2',
    border: '1px solid #fecaca',
    borderRadius: 8,
    fontSize: 13,
    color: '#dc2626',
  },
  errorDot: {
    fontSize: 6,
    color: '#ef4444',
  },
  button: {
    marginTop: 4,
    height: 44,
    background: 'linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)',
    color: '#fff',
    border: 'none',
    borderRadius: 10,
    fontSize: 15,
    fontWeight: 600,
    cursor: 'pointer',
    transition: 'opacity 0.15s, transform 0.1s',
    letterSpacing: '0.1px',
  },
  loadingRow: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  spinner: {
    width: 14,
    height: 14,
    border: '2px solid rgba(255,255,255,0.3)',
    borderTopColor: '#fff',
    borderRadius: '50%',
    display: 'inline-block',
    animation: 'spin 0.7s linear infinite',
  },
  divider: {
    display: 'flex',
    alignItems: 'center',
    gap: 12,
    margin: '24px 0',
  },
  dividerLine: {
    flex: 1,
    height: 1,
    background: '#e5e7eb',
    display: 'block',
  },
  dividerText: {
    fontSize: 12,
    color: '#9ca3af',
    flexShrink: 0,
  },
  footer: {
    textAlign: 'center',
    fontSize: 14,
    color: '#6b7280',
    margin: 0,
  },
  link: {
    color: '#4f46e5',
    fontWeight: 600,
    textDecoration: 'none',
  },
};

export default LoginPage;
