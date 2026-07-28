import { useState, useEffect } from 'react';
import { getServicesHealth } from '../../api/monitoring';
import type { ServiceHealth } from '../../types';

// 자동 폴링 주기(ms)
const POLL_INTERVAL_MS = 15000;

interface StatusStyle {
  text: string;
  bg: string;
}

const STATUS_STYLE: Record<string, StatusStyle> = {
  UP:   { text: 'UP',   bg: '#22c55e' },
  DOWN: { text: 'DOWN', bg: '#ef4444' },
};

const AdminMonitoringPage = () => {
  const [services, setServices] = useState<ServiceHealth[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);

  // 헬스 상태 수집 — 수동 새로고침 시 refreshing 표시
  const load = (manual = false) => {
    if (manual) setRefreshing(true);
    return getServicesHealth()
      .then((res) => {
        setServices(res.data ?? []);
        setUpdatedAt(new Date());
        setError('');
      })
      .catch(() => setError('서비스 상태를 불러오지 못했습니다.'))
      .finally(() => { setLoading(false); setRefreshing(false); });
  };

  useEffect(() => {
    // 최초 수집 + 15초 자동 폴링 — 언마운트 시 정리
    // (실제 setState는 비동기 .then/.finally 안에서만 발생 → 즉시 setState 아님)
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
    const timer = setInterval(() => load(), POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, []);

  const formatTime = (d: Date | null) =>
    d ? d.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '';

  if (loading) {
    return (
      <div className="flex justify-center items-center py-32">
        <div className="w-7 h-7 rounded-full animate-spin border-[3px] border-gray-200 border-t-brand-600" />
      </div>
    );
  }

  return (
    <div className="max-w-[860px] mx-auto">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-bold text-gray-900 m-0">서비스 모니터링</h1>
        <div className="flex items-center gap-3">
          {updatedAt && (
            <span className="text-[12px] text-gray-400">갱신 {formatTime(updatedAt)}</span>
          )}
          <button onClick={() => load(true)} disabled={refreshing}
            className="h-9 px-4 text-[13px] font-medium text-amber-600 border border-amber-200 rounded-[10px] hover:bg-amber-50 bg-white transition-colors disabled:opacity-60">
            {refreshing ? '새로고침 중...' : '새로고침'}
          </button>
        </div>
      </div>

      {error && <div className="error-box mb-4">{error}</div>}

      {services.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-24 text-center">
          <p className="text-gray-400 text-[15px] m-0">수집된 서비스 상태가 없습니다.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {services.map((svc) => {
            const style = STATUS_STYLE[svc.status] ?? { text: svc.status, bg: '#6b7280' };
            return (
              <div key={svc.name} className="bg-white border border-gray-100 rounded-2xl p-5">
                <div className="flex items-center justify-between mb-3">
                  <span className="text-[15px] font-bold text-gray-900">{svc.name}</span>
                  <span className="text-[12px] font-bold px-2.5 py-0.5 rounded-full text-white"
                    style={{ background: style.bg }}>{style.text}</span>
                </div>
                <div className="flex items-center justify-between text-[13px]">
                  <span className="text-gray-500">응답 시간</span>
                  <span className="font-medium text-gray-900">{svc.responseTimeMs} ms</span>
                </div>
                {svc.error && (
                  <div className="mt-2 text-[11px] text-red-500 bg-red-50 px-2 py-1 rounded break-all">
                    {svc.error}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default AdminMonitoringPage;
