import { useState, useEffect } from 'react';
import { getFailedOrders } from '../../api/order';
import type { FailedOrder } from '../../types';

// M-3: 재고 확보 실패 등으로 자동취소된 주문 조회 (ADMIN)
const AdminFailedOrderPage = () => {
  const [rows, setRows] = useState<FailedOrder[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');

  const load = (manual = false) => {
    if (manual) setRefreshing(true);
    return getFailedOrders(0, 20)
      .then((res) => {
        setRows(res.data.content ?? []);
        setError('');
      })
      .catch(() => setError('실패 주문 목록을 불러오지 못했습니다.'))
      .finally(() => { setLoading(false); setRefreshing(false); });
  };

  useEffect(() => {
    load();
  }, []);

  const formatDateTime = (v?: string) =>
    v ? new Date(v).toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }) : '';

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
        <h1 className="text-xl font-bold text-gray-900 m-0">실패 주문</h1>
        <button onClick={() => load(true)} disabled={refreshing}
          className="h-9 px-4 text-[13px] font-medium text-amber-600 border border-amber-200 rounded-[10px] hover:bg-amber-50 bg-white transition-colors disabled:opacity-60">
          {refreshing ? '새로고침 중...' : '새로고침'}
        </button>
      </div>

      {error && <div className="error-box mb-4">{error}</div>}

      {rows.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-24 text-center">
          <p className="text-gray-400 text-[15px] m-0">자동취소된 주문이 없습니다.</p>
        </div>
      ) : (
        <div className="bg-white border border-gray-100 rounded-2xl overflow-hidden">
          <table className="w-full text-[13px]">
            <thead>
              <tr className="text-left text-gray-500 border-b border-gray-100">
                <th className="px-4 py-3 font-medium">주문 ID</th>
                <th className="px-4 py-3 font-medium">사용자</th>
                <th className="px-4 py-3 font-medium">실패 사유</th>
                <th className="px-4 py-3 font-medium">발생 시각</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.orderId} className="border-b border-gray-50 last:border-0">
                  <td className="px-4 py-3 text-gray-900 font-medium">#{row.orderId}</td>
                  <td className="px-4 py-3 text-gray-700">{row.userId}</td>
                  <td className="px-4 py-3 text-gray-700">{row.reason}</td>
                  <td className="px-4 py-3 text-gray-400">{formatDateTime(row.occurredAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default AdminFailedOrderPage;
