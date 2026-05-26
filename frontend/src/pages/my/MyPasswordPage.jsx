import { useState } from 'react';

const MyPasswordPage = () => {
  const [form, setForm] = useState({ current: '', next: '', confirm: '' });
  const [msg, setMsg] = useState('');

  const handleChange = (e) => setForm((p) => ({ ...p, [e.target.name]: e.target.value }));

  const handleSubmit = (e) => {
    e.preventDefault();
    // TODO: 비밀번호 변경 API 연동
    setMsg('비밀번호 변경 기능은 추후 구현 예정입니다.');
  };

  return (
    <div style={styles.page}>
      <div style={styles.card}>
        <h2 style={styles.title}>비밀번호 변경</h2>
        <form onSubmit={handleSubmit} style={styles.form}>
          {[
            { name: 'current', label: '현재 비밀번호' },
            { name: 'next',    label: '새 비밀번호' },
            { name: 'confirm', label: '새 비밀번호 확인' },
          ].map(({ name, label }) => (
            <div key={name} style={styles.field}>
              <label style={styles.label}>{label}</label>
              <input
                type="password" name={name} value={form[name]}
                onChange={handleChange} style={styles.input}
                placeholder={label}
              />
            </div>
          ))}
          {msg && <p style={styles.msg}>{msg}</p>}
          <button type="submit" style={styles.btn}>변경하기</button>
        </form>
      </div>
    </div>
  );
};

const styles = {
  page: { maxWidth: 480, margin: '48px auto', padding: '0 16px' },
  card: {
    background: '#fff', borderRadius: 16, padding: '32px 36px',
    boxShadow: '0 2px 16px rgba(0,0,0,0.08)', border: '1px solid #e5e7eb',
  },
  title: { fontSize: 20, fontWeight: 700, color: '#111827', margin: '0 0 24px' },
  form: { display: 'flex', flexDirection: 'column', gap: 16 },
  field: { display: 'flex', flexDirection: 'column', gap: 6 },
  label: { fontSize: 13, fontWeight: 500, color: '#374151' },
  input: {
    height: 44, padding: '0 14px', border: '1.5px solid #e5e7eb',
    borderRadius: 10, fontSize: 14, outline: 'none', boxSizing: 'border-box',
  },
  msg: { fontSize: 13, color: '#6b7280', margin: 0 },
  btn: {
    height: 46, background: 'linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)',
    color: '#fff', border: 'none', borderRadius: 10,
    fontSize: 15, fontWeight: 600, cursor: 'pointer', marginTop: 4,
  },
};

export default MyPasswordPage;
