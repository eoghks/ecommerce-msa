import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { register, checkEmail } from '../../api/auth';

const IconUser = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#9ca3af" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
    <circle cx="12" cy="7" r="4" />
  </svg>
);

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

const IconCheck = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#9ca3af" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="20 6 9 17 4 12" />
  </svg>
);

const IconShop = () => (
  <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#4f46e5" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z" />
    <line x1="3" y1="6" x2="21" y2="6" />
    <path d="M16 10a4 4 0 0 1-8 0" />
  </svg>
);

const getPasswordStrength = (pw) => {
  if (!pw) return { level: 0, label: '', color: '' };
  let score = 0;
  if (pw.length >= 8) score++;
  if (/[A-Z]/.test(pw)) score++;
  if (/[0-9]/.test(pw)) score++;
  if (/[^A-Za-z0-9]/.test(pw)) score++;
  if (score <= 1) return { level: score, label: '약함', color: '#ef4444' };
  if (score === 2) return { level: score, label: '보통', color: '#f59e0b' };
  return { level: score, label: '강함', color: '#22c55e' };
};

const RegisterPage = () => {
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: '', email: '', password: '', passwordConfirm: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [focused, setFocused] = useState('');
  const [emailCheck, setEmailCheck] = useState({ checked: false, available: false, checking: false });

  useEffect(() => {
    document.body.style.background = '#f5f5ff';
    return () => { document.body.style.background = ''; };
  }, []);

  const pwStrength = getPasswordStrength(form.password);

  const handleChange = (e) => {
    setError('');
    const { name, value } = e.target;
    setForm((prev) => ({ ...prev, [name]: value }));
    if (name === 'email') setEmailCheck({ checked: false, available: false, checking: false });
  };

  const handleCheckEmail = async () => {
    if (!form.email) { setError('이메일을 입력해주세요.'); return; }
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(form.email)) { setError('올바른 이메일 형식이 아닙니다.'); return; }
    setEmailCheck((prev) => ({ ...prev, checking: true }));
    setError('');
    try {
      const res = await checkEmail(form.email);
      setEmailCheck({ checked: true, available: res.data.available, checking: false });
    } catch {
      setError('이메일 확인 중 오류가 발생했습니다.');
      setEmailCheck({ checked: false, available: false, checking: false });
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!form.name || !form.email || !form.password || !form.passwordConfirm) { setError('모든 항목을 입력해주세요.'); return; }
    if (!emailCheck.checked || !emailCheck.available) { setError('이메일 중복 체크를 완료해주세요.'); return; }
    if (form.password.length < 8) { setError('비밀번호는 8자 이상이어야 합니다.'); return; }
    if (!/[a-z]/.test(form.password)) { setError('비밀번호에 소문자를 포함해야 합니다.'); return; }
    if (!/[A-Z]/.test(form.password)) { setError('비밀번호에 대문자를 포함해야 합니다.'); return; }
    if (!/[0-9]/.test(form.password)) { setError('비밀번호에 숫자를 포함해야 합니다.'); return; }
    if (!/[!@#$%^&*()_+\-=\[\]{}|;':",./<>?]/.test(form.password)) { setError('비밀번호에 특수문자를 포함해야 합니다.'); return; }
    if (form.password !== form.passwordConfirm) { setError('비밀번호가 일치하지 않습니다.'); return; }
    setLoading(true);
    try {
      await register(form.email, form.password, form.name);
      navigate('/login', { state: { registered: true } });
    } catch (err) {
      setError(err.response?.data?.message || '회원가입에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const inputCls = (name) =>
    `input-field ${focused === name ? 'border-brand-600 shadow-[0_0_0_3px_rgba(79,70,229,0.12)]' : ''}`;

  const renderEmailStatus = () => {
    if (emailCheck.checking) return <span className="text-[11px] text-gray-500">확인 중...</span>;
    if (!emailCheck.checked) return null;
    if (emailCheck.available) return <span className="text-[11px] text-green-500 font-medium">✓ 사용 가능한 이메일입니다</span>;
    return <span className="text-[11px] text-red-500 font-medium">✗ 이미 사용 중인 이메일입니다</span>;
  };

  return (
    <div className="auth-page">
      <div className="auth-bg-circle-1" />
      <div className="auth-bg-circle-2" />

      <div className="auth-card">
        <div className="auth-logo">
          <div className="auth-logo-icon"><IconShop /></div>
          <span className="auth-logo-text">ShopMSA</span>
        </div>

        <h1 className="auth-title">시작해볼까요?</h1>
        <p className="auth-subtitle">무료 계정을 만들고 쇼핑을 시작하세요</p>

        <form onSubmit={handleSubmit} className="flex flex-col gap-3.5">
          {/* 이름 */}
          <div className="flex flex-col gap-1.5">
            <label className="field-label">이름</label>
            <div className="input-wrapper">
              <span className="input-icon"><IconUser /></span>
              <input type="text" name="name" value={form.name} onChange={handleChange}
                onFocus={() => setFocused('name')} onBlur={() => setFocused('')}
                placeholder="이름을 입력하세요" className={inputCls('name')} autoComplete="name" />
            </div>
          </div>

          {/* 이메일 + 중복 체크 */}
          <div className="flex flex-col gap-1.5">
            <label className="field-label">이메일</label>
            <div className="flex">
              <div className="input-wrapper flex-1">
                <span className="input-icon"><IconEmail /></span>
                <input type="email" name="email" value={form.email} onChange={handleChange}
                  onFocus={() => setFocused('email')} onBlur={() => setFocused('')}
                  placeholder="이메일을 입력하세요"
                  className={`${inputCls('email')} rounded-r-none`}
                  autoComplete="email" />
              </div>
              <button type="button" onClick={handleCheckEmail}
                disabled={emailCheck.checking || !form.email}
                className="shrink-0 h-11 px-4 text-white text-[13px] font-semibold rounded-r-[10px] border-none transition-all whitespace-nowrap disabled:opacity-50"
                style={{ background: emailCheck.available ? '#22c55e' : '#4f46e5' }}>
                {emailCheck.checking ? '확인 중' : emailCheck.available ? '✓ 확인됨' : '중복 확인'}
              </button>
            </div>
            {renderEmailStatus()}
          </div>

          {/* 비밀번호 */}
          <div className="flex flex-col gap-1.5">
            <label className="field-label">비밀번호</label>
            <div className="input-wrapper">
              <span className="input-icon"><IconLock /></span>
              <input type="password" name="password" value={form.password} onChange={handleChange}
                onFocus={() => setFocused('password')} onBlur={() => setFocused('')}
                placeholder="8자 이상 입력하세요" className={inputCls('password')} autoComplete="new-password" />
            </div>
            {/* 조건 표시 */}
            <div className="flex flex-wrap gap-x-3 gap-y-1 mt-1">
              {[
                { label: '8자 이상', ok: form.password.length >= 8 },
                { label: '소문자', ok: /[a-z]/.test(form.password) },
                { label: '대문자', ok: /[A-Z]/.test(form.password) },
                { label: '숫자', ok: /[0-9]/.test(form.password) },
                { label: '특수문자', ok: /[!@#$%^&*()_+\-=\[\]{}|;':",./<>?]/.test(form.password) },
              ].map(({ label, ok }) => (
                <span key={label} className={`text-[11px] font-medium ${ok ? 'text-green-500' : 'text-gray-400'}`}>
                  {ok ? '✓' : '○'} {label}
                </span>
              ))}
            </div>
            {/* 강도 바 */}
            {form.password && (
              <div className="flex items-center gap-2 mt-1">
                <div className="flex gap-1 flex-1">
                  {[1,2,3,4].map((i) => (
                    <div key={i} className="flex-1 h-[3px] rounded-sm transition-all duration-200"
                      style={{ background: i <= pwStrength.level ? pwStrength.color : '#e5e7eb' }} />
                  ))}
                </div>
                <span className="text-[11px] font-medium min-w-[24px]" style={{ color: pwStrength.color }}>{pwStrength.label}</span>
              </div>
            )}
          </div>

          {/* 비밀번호 확인 */}
          <div className="flex flex-col gap-1.5">
            <label className="field-label">비밀번호 확인</label>
            <div className="input-wrapper">
              <span className="input-icon"><IconCheck /></span>
              <input type="password" name="passwordConfirm" value={form.passwordConfirm} onChange={handleChange}
                onFocus={() => setFocused('passwordConfirm')} onBlur={() => setFocused('')}
                placeholder="비밀번호를 다시 입력하세요" className={inputCls('passwordConfirm')} autoComplete="new-password" />
            </div>
            {form.passwordConfirm && (
              <span className={`text-[11px] font-medium ${form.password === form.passwordConfirm ? 'text-green-500' : 'text-red-500'}`}>
                {form.password === form.passwordConfirm ? '✓ 비밀번호가 일치합니다' : '✗ 비밀번호가 일치하지 않습니다'}
              </span>
            )}
          </div>

          {error && <div className="error-box"><span className="text-[6px] text-red-400">●</span>{error}</div>}

          <button type="submit" disabled={loading} className="btn-primary mt-1">
            {loading
              ? <span className="flex items-center justify-center gap-2"><span className="spinner" />처리 중...</span>
              : '회원가입'}
          </button>
        </form>

        <div className="divider">
          <span className="divider-line" />
          <span className="divider-text">이미 계정이 있으신가요?</span>
          <span className="divider-line" />
        </div>
        <p className="text-center m-0">
          <Link to="/login" className="text-brand-600 font-semibold no-underline text-sm">로그인하러 가기 →</Link>
        </p>
      </div>
    </div>
  );
};

export default RegisterPage;
