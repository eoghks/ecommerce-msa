import { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { getMe, changePassword } from '../../api/auth';
import useAuthStore from '../../store/authStore';

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
  const location = useLocation();
  const { logout: clearAuth } = useAuthStore();

  const forceChange = location.state?.forceChange ?? false;

  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [pwOpen, setPwOpen] = useState(forceChange);
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
    if (pwOpen) { setPwForm({ current: '', next: '', confirm: '' }); setPwError(''); }
  };

  const handlePwSubmit = async (e) => {
    e.preventDefault();
    setPwError('');
    if (!pwForm.current || !pwForm.next || !pwForm.confirm) { setPwError('모든 항목을 입력해주세요.'); return; }
    if (pwForm.next !== pwForm.confirm) { setPwError('새 비밀번호가 일치하지 않습니다.'); return; }
    if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%^&*()_+\-=\[\]{}|;':",./<>?]).{8,}$/.test(pwForm.next)) {
      setPwError('새 비밀번호는 8자 이상, 대/소문자·숫자·특수문자를 포함해야 합니다.'); return;
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
    <div className="flex justify-center items-center h-[300px]">
      <div className="w-7 h-7 rounded-full animate-spin border-[3px] border-gray-200 border-t-brand-600" />
    </div>
  );

  return (
    <div className="max-w-[560px] mx-auto mt-6 sm:mt-10 px-4 flex flex-col gap-4 pb-10">

      {/* 강제 변경 배너 */}
      {forceChange && (
        <div className="force-banner">
          <span className="text-xl shrink-0">⚠️</span>
          <div>
            <strong>임시 비밀번호로 로그인되었습니다.</strong><br />
            <span className="text-[13px]">보안을 위해 아래에서 비밀번호를 변경해주세요.</span>
          </div>
        </div>
      )}

      {/* 프로필 헤더 */}
      <div className="card flex items-center gap-4 sm:gap-5">
        <div className="w-[52px] h-[52px] sm:w-[60px] sm:h-[60px] rounded-full shrink-0 flex items-center justify-center text-white text-xl sm:text-2xl font-bold"
          style={{ background: 'linear-gradient(135deg, #4f46e5, #6366f1)' }}>
          {getInitial(profile?.name)}
        </div>
        <div className="flex flex-col gap-1 flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-base sm:text-[18px] font-bold text-gray-900">{profile?.name || '-'}</span>
            <span className="px-2.5 py-[3px] rounded-full text-[11px] font-semibold"
              style={{
                background: profile?.role === 'ADMIN' ? '#fef3c7' : '#eef2ff',
                color: profile?.role === 'ADMIN' ? '#d97706' : '#4f46e5',
              }}>
              {profile?.role}
            </span>
          </div>
          <div className="text-[13px] text-gray-500 truncate">{profile?.email}</div>
          <div className="text-[12px] text-gray-400 mt-0.5">가입일 · {formatDate(profile?.createdAt)}</div>
        </div>
      </div>

      {/* 보안 카드 */}
      <div className="card">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="text-[15px] font-bold text-gray-900 mb-1">보안</div>
            <div className="text-[13px] text-gray-500 hidden sm:block">비밀번호를 정기적으로 변경하면 계정을 안전하게 보호할 수 있습니다.</div>
          </div>
          <button onClick={handlePwToggle}
            className="shrink-0 h-[34px] px-3 sm:px-4 text-[13px] font-medium rounded-lg transition-colors duration-150 whitespace-nowrap"
            style={pwOpen
              ? { background: 'transparent', border: '1.5px solid #e5e7eb', color: '#6b7280' }
              : { background: 'transparent', border: '1.5px solid #4f46e5', color: '#4f46e5' }}>
            {pwOpen ? '취소' : '비밀번호 변경'}
          </button>
        </div>

        {pwOpen && (
          <div>
            <div className="h-px bg-gray-100 my-5" />
            <form onSubmit={handlePwSubmit} className="flex flex-col gap-3.5">
              {/* 현재 비밀번호 */}
              <div className="flex flex-col gap-1.5">
                <label className="field-label">현재 비밀번호</label>
                <input type="password" name="current" value={pwForm.current} onChange={handlePwChange}
                  placeholder="현재 비밀번호 입력"
                  className="input-field pl-3" autoComplete="current-password" />
              </div>

              {/* 새 비밀번호 */}
              <div className="flex flex-col gap-1.5">
                <label className="field-label">새 비밀번호</label>
                <input type="password" name="next" value={pwForm.next} onChange={handlePwChange}
                  placeholder="8자 이상, 대/소문자·숫자·특수문자 포함"
                  className="input-field pl-3" autoComplete="new-password" />
                <div className="flex flex-wrap gap-x-3 gap-y-1 mt-1">
                  {[
                    { label: '8자 이상', ok: pwForm.next.length >= 8 },
                    { label: '소문자', ok: /[a-z]/.test(pwForm.next) },
                    { label: '대문자', ok: /[A-Z]/.test(pwForm.next) },
                    { label: '숫자', ok: /[0-9]/.test(pwForm.next) },
                    { label: '특수문자', ok: /[!@#$%^&*()_+\-=\[\]{}|;':",./<>?]/.test(pwForm.next) },
                  ].map(({ label, ok }) => (
                    <span key={label} className={`text-[11px] font-medium ${ok ? 'text-green-500' : 'text-gray-400'}`}>
                      {ok ? '✓' : '○'} {label}
                    </span>
                  ))}
                </div>
                {pwForm.next && (() => {
                  const s = getPasswordStrength(pwForm.next);
                  return (
                    <div className="flex items-center gap-2 mt-1.5">
                      <div className="flex gap-1 flex-1">
                        {[1,2,3,4].map((i) => (
                          <div key={i} className="flex-1 h-1 rounded-sm transition-all duration-200"
                            style={{ background: i <= s.level ? s.color : '#e5e7eb' }} />
                        ))}
                      </div>
                      <span className="text-[11px] font-medium min-w-[24px]" style={{ color: s.color }}>{s.label}</span>
                    </div>
                  );
                })()}
              </div>

              {/* 새 비밀번호 확인 */}
              <div className="flex flex-col gap-1.5">
                <label className="field-label">새 비밀번호 확인</label>
                <input type="password" name="confirm" value={pwForm.confirm} onChange={handlePwChange}
                  placeholder="새 비밀번호 재입력"
                  className="input-field pl-3" autoComplete="new-password" />
              </div>

              {pwError && <div className="error-box"><span className="text-[6px] text-red-400">●</span> {pwError}</div>}

              <div className="flex items-center justify-between gap-3 flex-wrap">
                <span className="text-[11px] text-gray-400">변경 후 보안을 위해 자동 로그아웃됩니다.</span>
                <button type="submit" disabled={pwLoading}
                  className="h-10 px-5 text-white text-sm font-semibold rounded-[10px] border-none disabled:opacity-70 transition-opacity shrink-0"
                  style={{ background: 'linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)' }}>
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

export default MyProfilePage;
