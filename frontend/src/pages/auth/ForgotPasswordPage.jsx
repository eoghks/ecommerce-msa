import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { forgotPassword } from '../../api/auth';

const IconShop = () => (
  <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#4f46e5" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z" />
    <line x1="3" y1="6" x2="21" y2="6" />
    <path d="M16 10a4 4 0 0 1-8 0" />
  </svg>
);

const IconEmail = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#9ca3af" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="2" y="4" width="20" height="16" rx="2" />
    <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7" />
  </svg>
);

const ForgotPasswordPage = () => {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [tempPassword, setTempPassword] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [focused, setFocused] = useState(false);

  useEffect(() => {
    document.body.style.background = '#f5f5ff';
    return () => { document.body.style.background = ''; };
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!email) { setError('이메일을 입력해주세요.'); return; }
    setLoading(true);
    try {
      const res = await forgotPassword(email);
      setSubmitted(true);
      if (res.data.tempPassword) setTempPassword(res.data.tempPassword);
    } catch (err) {
      setError(err.response?.data?.detail || err.response?.data?.message || '이메일 조회에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

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

        {!submitted ? (
          <>
            <h1 className="auth-title">비밀번호 찾기</h1>
            <p className="auth-subtitle">가입 시 사용한 이메일로 임시 비밀번호를 발급합니다.</p>

            <form onSubmit={handleSubmit} className="flex flex-col gap-[18px]">
              <div className="flex flex-col gap-1.5">
                <label className="field-label">이메일</label>
                <div className="input-wrapper">
                  <span className="input-icon"><IconEmail /></span>
                  <input
                    type="email" value={email}
                    onChange={(e) => { setError(''); setEmail(e.target.value); }}
                    onFocus={() => setFocused(true)}
                    onBlur={() => setFocused(false)}
                    placeholder="가입한 이메일 입력"
                    className={`input-field ${focused ? 'border-brand-600 shadow-[0_0_0_3px_rgba(79,70,229,0.12)]' : ''}`}
                    autoComplete="email"
                  />
                </div>
              </div>

              {error && <div className="error-box"><span className="text-[6px] text-red-400">●</span> {error}</div>}

              <button type="submit" disabled={loading} className="btn-primary mt-1">
                {loading
                  ? <span className="flex items-center justify-center gap-2"><span className="spinner" />발급 중...</span>
                  : '임시 비밀번호 발급'}
              </button>
            </form>
          </>
        ) : (
          /* 발급 완료 화면 */
          <div className="flex flex-col items-center gap-3 py-2 pb-4">
            <div className="w-14 h-14 rounded-full flex items-center justify-center text-white text-2xl font-bold"
              style={{ background: 'linear-gradient(135deg, #4f46e5, #6366f1)' }}>
              ✓
            </div>
            <h2 className="text-xl font-bold text-gray-900 m-0">임시 비밀번호 발급 완료</h2>

            {tempPassword ? (
              <>
                <p className="text-sm text-gray-500 text-center leading-relaxed m-0">임시 비밀번호가 발급되었습니다.</p>
                <div className="w-full flex flex-col items-center gap-1 px-[18px] py-3.5 rounded-[10px]"
                  style={{ background: '#f5f3ff', border: '1.5px solid #c4b5fd' }}>
                  <span className="text-[11px] text-purple-700 font-semibold tracking-wide">임시 비밀번호</span>
                  <span className="text-[22px] font-bold text-brand-600 tracking-[2px] font-mono">{tempPassword}</span>
                </div>
                <p className="text-xs text-gray-400 m-0">로그인 후 반드시 비밀번호를 변경해주세요.</p>
              </>
            ) : (
              <p className="text-sm text-gray-500 text-center leading-relaxed m-0">
                입력하신 이메일로 임시 비밀번호를 발송했습니다.<br />
                이메일을 확인하고 로그인 후 비밀번호를 변경해주세요.
              </p>
            )}
          </div>
        )}

        <p className="text-center text-sm text-gray-500 mt-6 mb-0">
          <Link to="/login" className="text-brand-600 font-semibold no-underline">← 로그인으로 돌아가기</Link>
        </p>
      </div>
    </div>
  );
};

export default ForgotPasswordPage;
