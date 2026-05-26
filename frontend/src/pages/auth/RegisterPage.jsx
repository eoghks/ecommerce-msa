import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { register, checkEmail } from '../../api/auth';

// 아이콘 컴포넌트
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

// 비밀번호 강도 계산
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
  const [focusedField, setFocusedField] = useState('');

  // body 배경을 페이지 배경색으로 통일 — 스크롤 시 흰 배경 노출 방지
  useEffect(() => {
    document.body.style.background = '#f5f5ff';
    return () => { document.body.style.background = ''; };
  }, []);

  // 이메일 중복 체크 상태
  const [emailCheck, setEmailCheck] = useState({ checked: false, available: false, checking: false });

  const pwStrength = getPasswordStrength(form.password);

  const handleChange = (e) => {
    setError('');
    const { name, value } = e.target;
    setForm((prev) => ({ ...prev, [name]: value }));
    // 이메일 변경 시 중복 체크 초기화
    if (name === 'email') {
      setEmailCheck({ checked: false, available: false, checking: false });
    }
  };

  // 이메일 중복 체크
  const handleCheckEmail = async () => {
    if (!form.email) {
      setError('이메일을 입력해주세요.');
      return;
    }
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(form.email)) {
      setError('올바른 이메일 형식이 아닙니다.');
      return;
    }
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

    if (!form.name || !form.email || !form.password || !form.passwordConfirm) {
      setError('모든 항목을 입력해주세요.');
      return;
    }
    if (!emailCheck.checked || !emailCheck.available) {
      setError('이메일 중복 체크를 완료해주세요.');
      return;
    }
    if (form.password.length < 8) {
      setError('비밀번호는 8자 이상이어야 합니다.');
      return;
    }
    if (form.password !== form.passwordConfirm) {
      setError('비밀번호가 일치하지 않습니다.');
      return;
    }

    setLoading(true);
    try {
      await register(form.email, form.password, form.name);
      navigate('/login', { state: { registered: true } });
    } catch (err) {
      const msg = err.response?.data?.message || '회원가입에 실패했습니다.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

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

  // 이메일 중복 체크 결과 표시
  const renderEmailStatus = () => {
    if (emailCheck.checking) return <span style={{ fontSize: 11, color: '#6b7280' }}>확인 중...</span>;
    if (!emailCheck.checked) return null;
    if (emailCheck.available) return <span style={{ fontSize: 11, color: '#22c55e', fontWeight: 500 }}>✓ 사용 가능한 이메일입니다</span>;
    return <span style={{ fontSize: 11, color: '#ef4444', fontWeight: 500 }}>✗ 이미 사용 중인 이메일입니다</span>;
  };

  return (
    <div style={styles.page}>
      <div style={styles.bgCircle1} />
      <div style={styles.bgCircle2} />

      <div style={styles.card}>
        {/* 로고 */}
        <div style={styles.logo}>
          <div style={styles.logoIcon}><IconShop /></div>
          <span style={styles.logoText}>ShopMSA</span>
        </div>

        <h1 style={styles.title}>시작해볼까요?</h1>
        <p style={styles.subtitle}>무료 계정을 만들고 쇼핑을 시작하세요</p>

        <form onSubmit={handleSubmit} style={styles.form}>

          {/* 이름 */}
          <div style={styles.field}>
            <label style={styles.label}>이름</label>
            <div style={styles.inputWrapper}>
              <span style={styles.inputIcon}><IconUser /></span>
              <input
                type="text" name="name" value={form.name}
                onChange={handleChange}
                onFocus={() => setFocusedField('name')}
                onBlur={() => setFocusedField('')}
                placeholder="이름을 입력하세요"
                style={inputStyle('name')}
                autoComplete="name"
              />
            </div>
          </div>

          {/* 이메일 + 중복 체크 버튼 */}
          <div style={styles.field}>
            <label style={styles.label}>이메일</label>
            <div style={styles.emailRow}>
              <div style={{ ...styles.inputWrapper, flex: 1 }}>
                <span style={styles.inputIcon}><IconEmail /></span>
                <input
                  type="email" name="email" value={form.email}
                  onChange={handleChange}
                  onFocus={() => setFocusedField('email')}
                  onBlur={() => setFocusedField('')}
                  placeholder="이메일을 입력하세요"
                  style={{ ...inputStyle('email'), borderRadius: '10px 0 0 10px' }}
                  autoComplete="email"
                />
              </div>
              <button
                type="button"
                onClick={handleCheckEmail}
                disabled={emailCheck.checking || !form.email}
                style={{
                  ...styles.checkBtn,
                  background: emailCheck.available ? '#22c55e' : '#4f46e5',
                  opacity: (!form.email || emailCheck.checking) ? 0.5 : 1,
                }}
              >
                {emailCheck.checking ? '확인 중' : emailCheck.available ? '✓ 확인됨' : '중복 확인'}
              </button>
            </div>
            <div style={{ minHeight: 16 }}>{renderEmailStatus()}</div>
          </div>

          {/* 비밀번호 */}
          <div style={styles.field}>
            <label style={styles.label}>비밀번호</label>
            <div style={styles.inputWrapper}>
              <span style={styles.inputIcon}><IconLock /></span>
              <input
                type="password" name="password" value={form.password}
                onChange={handleChange}
                onFocus={() => setFocusedField('password')}
                onBlur={() => setFocusedField('')}
                placeholder="8자 이상 입력하세요"
                style={inputStyle('password')}
                autoComplete="new-password"
              />
            </div>
            {form.password && (
              <div style={styles.strengthRow}>
                <div style={styles.strengthBars}>
                  {[1, 2, 3, 4].map((i) => (
                    <div key={i} style={{ ...styles.strengthBar, background: i <= pwStrength.level ? pwStrength.color : '#e5e7eb' }} />
                  ))}
                </div>
                <span style={{ fontSize: 11, color: pwStrength.color, fontWeight: 500 }}>{pwStrength.label}</span>
              </div>
            )}
          </div>

          {/* 비밀번호 확인 */}
          <div style={styles.field}>
            <label style={styles.label}>비밀번호 확인</label>
            <div style={styles.inputWrapper}>
              <span style={styles.inputIcon}><IconCheck /></span>
              <input
                type="password" name="passwordConfirm" value={form.passwordConfirm}
                onChange={handleChange}
                onFocus={() => setFocusedField('passwordConfirm')}
                onBlur={() => setFocusedField('')}
                placeholder="비밀번호를 다시 입력하세요"
                style={inputStyle('passwordConfirm')}
                autoComplete="new-password"
              />
            </div>
            {form.passwordConfirm && (
              <span style={{ fontSize: 11, color: form.password === form.passwordConfirm ? '#22c55e' : '#ef4444', fontWeight: 500 }}>
                {form.password === form.passwordConfirm ? '✓ 비밀번호가 일치합니다' : '✗ 비밀번호가 일치하지 않습니다'}
              </span>
            )}
          </div>

          {/* 에러 메시지 */}
          {error && (
            <div style={styles.errorBox}>
              <span style={styles.errorDot}>●</span>
              {error}
            </div>
          )}

          {/* 회원가입 버튼 */}
          <button type="submit" disabled={loading} style={{ ...styles.button, opacity: loading ? 0.7 : 1 }}>
            {loading ? (
              <span style={styles.loadingRow}><span style={styles.spinner} />처리 중...</span>
            ) : '회원가입'}
          </button>
        </form>

        <div style={styles.divider}>
          <span style={styles.dividerLine} />
          <span style={styles.dividerText}>이미 계정이 있으신가요?</span>
          <span style={styles.dividerLine} />
        </div>
        <p style={styles.footer}>
          <Link to="/login" style={styles.link}>로그인하러 가기 →</Link>
        </p>
      </div>

      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
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
    position: 'absolute', width: 400, height: 400, borderRadius: '50%',
    background: 'radial-gradient(circle, rgba(79,70,229,0.12) 0%, transparent 70%)',
    top: -100, right: -100, pointerEvents: 'none',
  },
  bgCircle2: {
    position: 'absolute', width: 300, height: 300, borderRadius: '50%',
    background: 'radial-gradient(circle, rgba(99,102,241,0.08) 0%, transparent 70%)',
    bottom: -80, left: -80, pointerEvents: 'none',
  },
  card: {
    position: 'relative', width: '100%', maxWidth: 420,
    background: '#ffffff', borderRadius: 20, padding: '28px 36px',
    boxShadow: '0 4px 32px rgba(79,70,229,0.10), 0 1px 4px rgba(0,0,0,0.06)',
    border: '1px solid rgba(79,70,229,0.08)',
  },
  logo: { display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 },
  logoIcon: {
    width: 40, height: 40, background: '#eef2ff', borderRadius: 12,
    display: 'flex', alignItems: 'center', justifyContent: 'center',
  },
  logoText: { fontSize: 18, fontWeight: 700, color: '#4f46e5', letterSpacing: '-0.3px' },
  title: { margin: '0 0 4px', fontSize: 22, fontWeight: 700, color: '#111827', letterSpacing: '-0.5px' },
  subtitle: { margin: '0 0 18px', fontSize: 13, color: '#6b7280' },
  form: { display: 'flex', flexDirection: 'column', gap: 12 },
  field: { display: 'flex', flexDirection: 'column', gap: 5 },
  label: { fontSize: 13, fontWeight: 500, color: '#374151' },
  inputWrapper: { position: 'relative', display: 'flex', alignItems: 'center' },
  inputIcon: { position: 'absolute', left: 12, display: 'flex', alignItems: 'center', pointerEvents: 'none' },
  emailRow: { display: 'flex', gap: 0 },
  checkBtn: {
    flexShrink: 0, height: 44, padding: '0 14px',
    color: '#fff', border: 'none',
    borderRadius: '0 10px 10px 0',
    fontSize: 13, fontWeight: 600, cursor: 'pointer',
    transition: 'background 0.2s, opacity 0.15s',
    whiteSpace: 'nowrap',
  },
  strengthRow: { display: 'flex', alignItems: 'center', gap: 8, marginTop: 2 },
  strengthBars: { display: 'flex', gap: 4, flex: 1 },
  strengthBar: { flex: 1, height: 3, borderRadius: 2, transition: 'background 0.2s' },
  errorBox: {
    display: 'flex', alignItems: 'center', gap: 6,
    padding: '10px 14px', background: '#fef2f2',
    border: '1px solid #fecaca', borderRadius: 8, fontSize: 13, color: '#dc2626',
  },
  errorDot: { fontSize: 6, color: '#ef4444' },
  button: {
    marginTop: 4, height: 44,
    background: 'linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)',
    color: '#fff', border: 'none', borderRadius: 10,
    fontSize: 15, fontWeight: 600, cursor: 'pointer',
    transition: 'opacity 0.15s', letterSpacing: '0.1px',
  },
  loadingRow: { display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 },
  spinner: {
    width: 14, height: 14,
    border: '2px solid rgba(255,255,255,0.3)', borderTopColor: '#fff',
    borderRadius: '50%', display: 'inline-block',
    animation: 'spin 0.7s linear infinite',
  },
  divider: { display: 'flex', alignItems: 'center', gap: 12, margin: '14px 0 10px' },
  dividerLine: { flex: 1, height: 1, background: '#e5e7eb', display: 'block' },
  dividerText: { fontSize: 12, color: '#9ca3af', flexShrink: 0, whiteSpace: 'nowrap' },
  footer: { textAlign: 'center', margin: 0 },
  link: { color: '#4f46e5', fontWeight: 600, textDecoration: 'none', fontSize: 14 },
};

export default RegisterPage;
