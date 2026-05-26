import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { register } from '../../api/auth';

const RegisterPage = () => {
  const navigate = useNavigate();

  const [form, setForm] = useState({ name: '', email: '', password: '', passwordConfirm: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // 입력 값 변경 처리
  const handleChange = (e) => {
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));
  };

  // 회원가입 제출
  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!form.name || !form.email || !form.password || !form.passwordConfirm) {
      setError('모든 항목을 입력해주세요.');
      return;
    }
    if (form.password !== form.passwordConfirm) {
      setError('비밀번호가 일치하지 않습니다.');
      return;
    }
    if (form.password.length < 8) {
      setError('비밀번호는 8자 이상이어야 합니다.');
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

  return (
    <div style={styles.container}>
      <div style={styles.card}>
        <h2 style={styles.title}>회원가입</h2>

        <form onSubmit={handleSubmit} style={styles.form}>
          <div style={styles.field}>
            <label style={styles.label}>이름</label>
            <input
              type="text"
              name="name"
              value={form.name}
              onChange={handleChange}
              placeholder="이름 입력"
              style={styles.input}
              autoComplete="name"
            />
          </div>

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
              placeholder="8자 이상 입력"
              style={styles.input}
              autoComplete="new-password"
            />
          </div>

          <div style={styles.field}>
            <label style={styles.label}>비밀번호 확인</label>
            <input
              type="password"
              name="passwordConfirm"
              value={form.passwordConfirm}
              onChange={handleChange}
              placeholder="비밀번호 재입력"
              style={styles.input}
              autoComplete="new-password"
            />
          </div>

          {error && <p style={styles.error}>{error}</p>}

          <button type="submit" disabled={loading} style={styles.button}>
            {loading ? '처리 중...' : '회원가입'}
          </button>
        </form>

        <p style={styles.footer}>
          이미 계정이 있으신가요?{' '}
          <Link to="/login" style={styles.link}>로그인</Link>
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

export default RegisterPage;
