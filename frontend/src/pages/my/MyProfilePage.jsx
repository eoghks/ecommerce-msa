import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { getMe, changePassword } from '../../api/auth';
import useAuthStore from '../../store/authStore';

// 비밀번호 강도 계산 (RegisterPage와 동일 로직)
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

const MyProfilePage = () => {
  const navigate = useNavigate();
  const { logout: clearAuth } = useAuthStore();

  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);

  // 비밀번호 변경 폼 열림 상태 (기본 접힘)
  const [pwOpen, setPwOpen] = useState(false);
  const [pwForm, setPwForm] = useState({ current: '', next: '', confirm: '' });
  const [pwError, setPwError] = useState('');
  const [pwLoading, setPwLoading] = useState(false);

  useEffect(() => {
    getMe()
      .then((res) => setProfile(res.data))
      .catch(() => navigate('/login'))
      .finally(() => setLoading(false));
  }, []);

  const handlePwChange = (e) => {
    setPwError('');
    setPwForm((p) => ({ ...p, [e.target.name]: e.target.value }));
  };

  const handlePwToggle = () => {
    setPwOpen((v) => !v);
    // 닫을 때 폼 초기화
    if (pwOpen) {
      setPwForm({ current: '', next: '', confirm: '' });
      setPwError('');
    }
  };

  const handlePwSubmit = async (e) => {
    e.preventDefault();
    setPwError('');
    if (!pwForm.current || !pwForm.next || !pwForm.confirm) {
      setPwError('모든 항목을 입력해주세요.');
      return;
    }
    if (pwForm.next !== pwForm.confirm) {
      setPwError('새 비밀번호가 일치하지 않습니다.');
      return;
    }
    if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%^&*()_+\-=\[\]{}|;':",./<>?]).{8,}$/.test(pwForm.next)) {
      setPwError('새 비밀번호는 8자 이상, 대/소문자·숫자·특수문자를 포함해야 합니다.');
      return;
    }
    setPwLoading(true);
    try {
      await changePassword(profile.email, pwForm.current, pwForm.next);
      clearAuth();
      navigate('/login', { state: { message: '비밀번호가 변경되었습니다. 다시 로그인해주세요.' } });
    } catch (err) {
      setPwError(err.response?.data?.message || '현재 비밀번호가 올바르지 않습니다.');
    } finally {
      setPwLoading(false);
    }
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' });
  };

  const getInitial = (name) => (name ? name.charAt(0).toUpperCase() : '?');

  if (loading) return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: 300 }}>
      <div style={{ width: 28, height: 28, border: '3px solid #e5e7eb', borderTopColor: '#4f46e5', borderRadius: '50%', animation: 'spin 0.7s linear infinite' }} />
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );

  return (
    <div style={styles.page}>

      {/* 프로필 헤더 — 계정 정보 통합 */}
      <div style={styles.headerCard}>
        <div style={styles.avatar}>{getInitial(profile?.name)}</div>

        <div style={styles.headerInfo}>
          <div style={styles.headerTop}>
            <span style={styles.profileName}>{profile?.name || '-'}</span>
            <span style={{
              ...styles.roleBadge,
              background: profile?.role === 'ADMIN' ? '#fef3c7' : '#eef2ff',
              color: profile?.role === 'ADMIN' ? '#d97706' : '#4f46e5',
            }}>
              {profile?.role}
            </span>
          </div>
          <div style={styles.profileEmail}>{profile?.email}</div>
          <div style={styles.profileJoined}>가입일 · {formatDate(profile?.createdAt)}</div>
        </div>
      </div>

      {/* 보안 카드 */}
      <div style={styles.card}>
        {/* 헤더 행 — 항상 표시 */}
        <div style={styles.secHeader}>
          <div>
            <div style={styles.sectionTitle}>보안</div>
            <div style={styles.sectionDesc}>비밀번호를 정기적으로 변경하면 계정을 안전하게 보호할 수 있습니다.</div>
          </div>
          <button onClick={handlePwToggle} style={{ ...styles.toggleBtn, ...(pwOpen ? styles.toggleBtnActive : {}) }}>
            {pwOpen ? '취소' : '비밀번호 변경'}
          </button>
        </div>

        {/* 비밀번호 폼 — 토글로 표시 */}
        {pwOpen && (
          <div style={styles.pwSection}>
            <div style={styles.divider} />
            <form onSubmit={handlePwSubmit} style={styles.pwForm}>

              {/* 현재 비밀번호 */}
              <div style={styles.field}>
                <label style={styles.fieldLabel}>현재 비밀번호</label>
                <input
                  type="password" name="current"
                  value={pwForm.current} onChange={handlePwChange}
                  placeholder="현재 비밀번호 입력" style={styles.input}
                  autoComplete="current-password"
                />
              </div>

              {/* 새 비밀번호 — 조건 + 강도 바 포함 */}
              <div style={styles.field}>
                <label style={styles.fieldLabel}>새 비밀번호</label>
                <input
                  type="password" name="next"
                  value={pwForm.next} onChange={handlePwChange}
                  placeholder="8자 이상, 대/소문자·숫자·특수문자 포함" style={styles.input}
                  autoComplete="new-password"
                />
                {/* 조건 표시 — 항상 노출 (RegisterPage와 동일) */}
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px 12px', marginTop: 4 }}>
                  {[
                    { label: '8자 이상',  ok: pwForm.next.length >= 8 },
                    { label: '소문자',    ok: /[a-z]/.test(pwForm.next) },
                    { label: '대문자',    ok: /[A-Z]/.test(pwForm.next) },
                    { label: '숫자',      ok: /[0-9]/.test(pwForm.next) },
                    { label: '특수문자',  ok: /[!@#$%^&*()_+\-=\[\]{}|;':",./<>?]/.test(pwForm.next) },
                  ].map(({ label, ok }) => (
                    <span key={label} style={{ fontSize: 11, color: ok ? '#22c55e' : '#9ca3af', fontWeight: 500 }}>
                      {ok ? '✓' : '○'} {label}
                    </span>
                  ))}
                </div>
                {/* 강도 바 — 입력 시에만 */}
                {pwForm.next && (() => {
                  const s = getPasswordStrength(pwForm.next);
                  return (
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 6 }}>
                      <div style={{ display: 'flex', gap: 3, flex: 1 }}>
                        {[1, 2, 3, 4].map((i) => (
                          <div key={i} style={{
                            flex: 1, height: 4, borderRadius: 2,
                            background: i <= s.level ? s.color : '#e5e7eb',
                            transition: 'background 0.2s',
                          }} />
                        ))}
                      </div>
                      <span style={{ fontSize: 11, color: s.color, fontWeight: 500, minWidth: 24 }}>{s.label}</span>
                    </div>
                  );
                })()}
              </div>

              {/* 새 비밀번호 확인 */}
              <div style={styles.field}>
                <label style={styles.fieldLabel}>새 비밀번호 확인</label>
                <input
                  type="password" name="confirm"
                  value={pwForm.confirm} onChange={handlePwChange}
                  placeholder="새 비밀번호 재입력" style={styles.input}
                  autoComplete="new-password"
                />
              </div>

              {pwError && (
                <div style={styles.errorBox}>
                  <span style={{ fontSize: 6, color: '#ef4444' }}>●</span> {pwError}
                </div>
              )}

              <div style={styles.pwFooter}>
                <span style={styles.notice}>변경 후 보안을 위해 자동 로그아웃됩니다.</span>
                <button type="submit" disabled={pwLoading} style={{ ...styles.submitBtn, opacity: pwLoading ? 0.7 : 1 }}>
                  {pwLoading ? '변경 중...' : '변경 완료'}
                </button>
              </div>
            </form>
          </div>
        )}
      </div>
    </div>
  );
};

const styles = {
  page: { maxWidth: 560, margin: '40px auto', padding: '0 16px', display: 'flex', flexDirection: 'column', gap: 16 },

  /* 헤더 카드 */
  headerCard: {
    display: 'flex', alignItems: 'center', gap: 20,
    background: '#fff', borderRadius: 16, padding: '24px 28px',
    boxShadow: '0 2px 16px rgba(0,0,0,0.07)', border: '1px solid #e5e7eb',
  },
  avatar: {
    width: 60, height: 60, borderRadius: '50%', flexShrink: 0,
    background: 'linear-gradient(135deg, #4f46e5, #6366f1)',
    color: '#fff', fontSize: 24, fontWeight: 700,
    display: 'flex', alignItems: 'center', justifyContent: 'center',
  },
  headerInfo:  { display: 'flex', flexDirection: 'column', gap: 4, flex: 1, minWidth: 0 },
  headerTop:   { display: 'flex', alignItems: 'center', gap: 10 },
  profileName: { fontSize: 18, fontWeight: 700, color: '#111827' },
  roleBadge: {
    padding: '3px 10px', borderRadius: 20, fontSize: 11, fontWeight: 600,
  },
  profileEmail:  { fontSize: 13, color: '#6b7280' },
  profileJoined: { fontSize: 12, color: '#9ca3af', marginTop: 2 },

  /* 보안 카드 */
  card: {
    background: '#fff', borderRadius: 16, padding: '24px 28px',
    boxShadow: '0 2px 16px rgba(0,0,0,0.07)', border: '1px solid #e5e7eb',
  },
  secHeader: { display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 16 },
  sectionTitle: { fontSize: 15, fontWeight: 700, color: '#111827', marginBottom: 4 },
  sectionDesc:  { fontSize: 13, color: '#6b7280' },

  /* 토글 버튼 */
  toggleBtn: {
    flexShrink: 0,
    height: 34, padding: '0 16px',
    background: 'transparent', border: '1.5px solid #4f46e5',
    borderRadius: 8, fontSize: 13, fontWeight: 500,
    color: '#4f46e5', cursor: 'pointer', whiteSpace: 'nowrap',
    transition: 'background 0.15s, color 0.15s',
  },
  toggleBtnActive: {
    border: '1.5px solid #e5e7eb',
    color: '#6b7280',
  },

  /* 비밀번호 폼 */
  divider:   { height: 1, background: '#f3f4f6', margin: '20px 0' },
  pwSection: {},
  pwForm:    { display: 'flex', flexDirection: 'column', gap: 14 },
  field:     { display: 'flex', flexDirection: 'column', gap: 6 },
  fieldLabel: { fontSize: 13, fontWeight: 500, color: '#374151' },
  input: {
    height: 44, padding: '0 14px',
    border: '1.5px solid #e5e7eb', borderRadius: 10,
    fontSize: 14, outline: 'none', boxSizing: 'border-box',
    transition: 'border-color 0.15s',
  },
  errorBox: {
    display: 'flex', alignItems: 'center', gap: 6,
    padding: '10px 14px', background: '#fef2f2',
    border: '1px solid #fecaca', borderRadius: 8,
    fontSize: 13, color: '#dc2626',
  },
  pwFooter: { display: 'flex', alignItems: 'center', justifyContent: 'space-between' },
  notice:   { fontSize: 11, color: '#9ca3af' },
  submitBtn: {
    height: 40, padding: '0 20px',
    background: 'linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)',
    color: '#fff', border: 'none', borderRadius: 10,
    fontSize: 14, fontWeight: 600, cursor: 'pointer',
  },
};

export default MyProfilePage;
