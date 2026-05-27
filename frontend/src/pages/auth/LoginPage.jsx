import { useState, useEffect } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { login } from '../../api/auth';
import useAuthStore from '../../store/authStore';

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
  const location = useLocation();
  const loginStore = useAuthStore((s) => s.login);

  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState('');
  const [info, setInfo] = useState(location.state?.message || '');
  const [loading, setLoading] = useState(false);
  const [focused, setFocused] = useState('');

  useEffect(() => {
    document.body.style.background = '#f5f5ff';
    return () => { document.body.style.background = ''; };
  }, []);

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
      const { accessToken } = res.data;
      loginStore(accessToken);

      const claims = JSON.parse(atob(accessToken.split('.')[1]));
      if (claims?.pwdChangeRequired) {
        navigate('/my/profile', { state: { forceChange: true } });
      } else {
        navigate('/products');
      }
    } catch (err) {
      const msg = err.response?.data?.message || '이메일 또는 비밀번호가 올바르지 않습니다.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const inputCls = (name) =>
    `input-field ${focused === name ? 'border-brand-600 shadow-[0_0_0_3px_rgba(79,70,229,0.12)]' : ''}`;

  return (
    <div className="auth-page">
      <div className="auth-bg-circle-1" />
      <div className="auth-bg-circle-2" />

      <div className="auth-card">
        {/* 로고 */}
        <div className="auth-logo">
          <div className="auth-logo-icon"><IconShop /></div>
          <span className="auth-logo-text">ShopMSA</span>
        </div>

        <h1 className="auth-title">다시 오셨군요!</h1>
        <p className="auth-subtitle">계정에 로그인하세요</p>

        <form onSubmit={handleSubmit} className="flex flex-col gap-[18px]">
          {/* 이메일 */}
          <div className="flex flex-col gap-1.5">
            <label className="field-label">이메일</label>
            <div className="input-wrapper">
              <span className="input-icon"><IconEmail /></span>
              <input
                type="email" name="email" value={form.email}
                onChange={handleChange}
                onFocus={() => setFocused('email')}
                onBlur={() => setFocused('')}
                placeholder="이메일을 입력하세요"
                className={inputCls('email')}
                autoComplete="email"
              />
            </div>
          </div>

          {/* 비밀번호 */}
          <div className="flex flex-col gap-1.5">
            <div className="flex justify-between items-center">
              <label className="field-label">비밀번호</label>
              <Link to="/forgot-password" className="text-xs text-brand-600 no-underline">
                비밀번호를 잊으셨나요?
              </Link>
            </div>
            <div className="input-wrapper">
              <span className="input-icon"><IconLock /></span>
              <input
                type="password" name="password" value={form.password}
                onChange={handleChange}
                onFocus={() => setFocused('password')}
                onBlur={() => setFocused('')}
                placeholder="비밀번호를 입력하세요"
                className={inputCls('password')}
                autoComplete="current-password"
              />
            </div>
          </div>

          {info && <div className="info-box"><span className="text-[6px] text-green-500">●</span>{info}</div>}
          {error && <div className="error-box"><span className="text-[6px] text-red-400">●</span>{error}</div>}

          <button type="submit" disabled={loading} className="btn-primary mt-1">
            {loading
              ? <span className="flex items-center justify-center gap-2"><span className="spinner" />로그인 중...</span>
              : '로그인'}
          </button>
        </form>

        <div className="divider">
          <span className="divider-line" />
          <span className="divider-text">또는</span>
          <span className="divider-line" />
        </div>

        <p className="text-center text-sm text-gray-500 m-0">
          아직 계정이 없으신가요?{' '}
          <Link to="/register" className="text-brand-600 font-semibold no-underline">회원가입</Link>
        </p>
      </div>
    </div>
  );
};

export default LoginPage;
