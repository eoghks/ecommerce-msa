import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { getMyReturns } from '../../api/return';
import { returnStatusStyle } from '../../utils/returnStatus';
import type { ReturnRequest } from '../../types';

const formatDateTime = (v?: string | null) =>
  v ? new Date(v).toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }) : '';

// V1.1-5: 내 반품 내역 — 상태 뱃지·사유·처리시각
const ReturnListPage = () => {
  const [rows, setRows] = useState<ReturnRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = () =>
    getMyReturns(0, 20)
      .then((res) => setRows(res.data.content ?? []))
      .catch(() => setError('반품 내역을 불러오지 못했습니다.'))
      .finally(() => setLoading(false));

  useEffect(() => { load(); }, []);

  if (loading) {
    return (
      <div className="flex justify-center items-center py-32">
        <div className="w-7 h-7 rounded-full animate-spin border-[3px] border-gray-200 border-t-brand-600" />
      </div>
    );
  }

  return (
    <div className="max-w-[720px] mx-auto">
      <h1 className="text-xl font-bold text-gray-900 mb-6 m-0">내 반품 내역</h1>

      {error && <div className="error-box mb-4">{error}</div>}

      {rows.length === 0 ? (
        <div className="flex flex-col items-center gap-4 py-24 text-center">
          <p className="text-gray-400 text-[15px] m-0">반품 신청 내역이 없습니다.</p>
          <Link to="/orders" className="btn-brand-fill no-underline text-sm">
            내 주문 보기
          </Link>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          {rows.map((row) => (
            <ReturnCard key={row.id} row={row} />
          ))}
        </div>
      )}
    </div>
  );
};

const ReturnCard = ({ row }: { row: ReturnRequest }) => {
  const status = returnStatusStyle(row.status);

  return (
    <div className="bg-white border border-gray-100 rounded-2xl p-5">
      {/* 헤더 — 상태 뱃지 + 신청시각 */}
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <span className="text-[12px] font-bold px-2 py-0.5 rounded-full text-white"
            style={{ background: status.color }}>
            {status.text}
          </span>
          <span className="text-[12px] text-gray-400">{formatDateTime(row.requestedAt)}</span>
        </div>
        <Link to="/orders" className="text-[12px] text-brand-600 no-underline hover:underline">
          주문 #{row.orderId}
        </Link>
      </div>

      {/* 신청 사유 */}
      <div className="text-[13px] text-gray-700 mb-1">
        <span className="text-gray-500 mr-2">반품 사유</span>
        {row.reason}
      </div>

      {/* 거부 사유 — 거부 건만 */}
      {row.rejectReason && (
        <div className="text-[13px] text-red-500 mb-1">
          <span className="text-gray-500 mr-2">거부 사유</span>
          {row.rejectReason}
        </div>
      )}

      <div className="h-px bg-gray-100 my-3" />
      <div className="flex justify-between text-[12px] text-gray-400">
        <span>주문 항목 #{row.orderItemId}</span>
        <span>{row.processedAt ? `처리 ${formatDateTime(row.processedAt)}` : '처리 대기'}</span>
      </div>
    </div>
  );
};

export default ReturnListPage;
