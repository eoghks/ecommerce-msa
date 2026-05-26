import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { login } from '../../api/auth';
import useAuthStore from '../../store/authStore';

const LoginPage = () => {
  const navigate = useNavigate();
  const loginStore = useAuthStore((s) => s.login);

  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // 입력 값 변경 처리
  const handleChange = (e) => {
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));
  };

  // 로그인 제출
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
      const msg = err.response?.data?.message || '로그인에 실패했습니다.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={styles.container}>
      <div style={styles.card}>
        <h2 style={styles.title}>로그인</h2>

        <form onSubmit={handleSubmit} style={styles.form}>
          <div style={styles.field}>
            <label style={styles.label}>이메일</label>
            <input
              type="email"
              name="email"
              value={form.email}
              onChange={handleChange}
              placeholder="이메일 입력"
              style={styles.input}
              autoComplete="email"
            />
          </div>

          <div style={styles.field}>
            <label style={styles.label}>비밀번호</label>
            <input
              type="password"
              name="password"
              value={form.password}
              onChange={handleChange}
              placeholder="비밀번호 입력"
              style={styles.input}
              autoComplete="current-password"
            />
          </div>

          {error && <p style={styles.error}>{error}</p>}

          <button type="submit" disabled={loading} style={styles.button}>
            {loading ? '로그인 중...' : '로그인'}
          </button>
        </form>

        <p style={styles.footer}>
          계정이 없으신가요?{' '}
          <Link to="/register" style={styles.link}>회원가입</Link>
        </p>
      </div>
    </div>
  );
};

const styles = {
  container: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    minHeight: 'calc(100vh - 56px)',
  },
  card: {
    width: '100%',
    maxWidth: 400,
    padding: '40px 32px',
    border: '1px solid #e5e7eb',
    borderRadius: 12,
    boxShadow: '0 2px 8px rgba(0,0,0,0.08)',
  },
  title: {
    margin: '0 0 24px',
    fontSize: 24,
    fontWeight: 700,
    textAlign: 'center',
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: 16,
  },
  field: {
    display: 'flex',
    flexDirection: 'column',
    gap: 6,
  },
  label: {
    fontSize: 14,
    fontWeight: 500,
    color: '#374151',
  },
  input: {
    padding: '10px 12px',
    border: '1px solid #d1d5db',
    borderRadius: 8,
    fontSize: 14,
    outline: 'none',
  },
  error: {
    margin: 0,
    fontSize: 13,
    color: '#ef4444',
  },
  button: {
    marginTop: 8,
    padding: '12px',
    backgroundColor: '#2563eb',
    color: '#fff',
    border: 'none',
    borderRadius: 8,
    fontSize: 15,
    fontWeight: 600,
    cursor: 'pointer',
  },
  footer: {
    marginTop: 20,
    textAlign: 'center',
    fontSize: 14,
    color: '#6b7280',
  },
  link: {
    color: '#2563eb',
    textDecoration: 'none',
    fontWeight: 500,
  },
};

export default LoginPage;
