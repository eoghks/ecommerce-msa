import { useState, useEffect } from 'react';
import type { AxiosError } from 'axios';
import { getManagedReturns, approveReturn, rejectReturn } from '../../api/return';
import useAuthStore from '../../store/authStore';
import { returnStatusStyle } from '../../utils/returnStatus';
import type { ApiErrorResponse, ReturnRequest } from '../../types';

const formatDateTime = (v?: string | null) =>
  v ? new Date(v).toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }) : '';

const errorDetail = (err: unknown, fallback: string) =>
  (err as AxiosError<ApiErrorResponse>).response?.data?.detail || fallback;

// V1.1-5: 반품 관리 (ADMIN 전체 / SELLER 본인 상품 포함 건만) — 승인·거부
const AdminReturnPage = () => {
  const { role } = useAuthStore();
  const isAdmin = role === 'ADMIN';

  const [rows, setRows] = useState<ReturnRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState<number | null>(null);

  // 거부 사유 모달 대상
  const [rejectTarget, setRejectTarget] = useState<ReturnRequest | null>(null);

  const load = () =>
    getManagedReturns(0, 50)
      .then((res) => setRows(res.data.content ?? []))
      .catch((err) => setError(errorDetail(err, '반품 목록을 불러오지 못했습니다.')))
      .finally(() => setLoading(false));

  useEffect(() => { load(); }, []);

  const handleApprove = async (row: ReturnRequest) => {
    if (!window.confirm(`반품 #${row.id}을 승인하시겠습니까?\n재고가 복구되고 환불이 처리됩니다.`)) return;
    setBusyId(row.id);
    setError('');
    try {
      await approveReturn(row.id);
      await load();
    } catch (err) {
      setError(errorDetail(err, '반품 승인에 실패했습니다.'));
    } finally {
      setBusyId(null);
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center py-32">
        <div className="w-7 h-7 rounded-full animate-spin border-[3px] border-gray-200 border-t-brand-600" />
      </div>
    );
  }

  return (
    <div className="max-w-[1000px] mx-auto">
      <h1 className="text-xl font-bold text-gray-900 mb-6 m-0">
        {isAdmin ? '반품 관리 (전체)' : '내 상품 반품'}
      </h1>

      {error && !rejectTarget && <div className="error-box mb-4">{error}</div>}

      {rows.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-24 text-center">
          <p className="text-gray-400 text-[15px] m-0">반품 신청 내역이 없습니다.</p>
        </div>
      ) : (
        <div className="bg-white border border-gray-100 rounded-2xl overflow-hidden">
          <table className="w-full text-[13px]">
            <thead>
              <tr className="text-left text-gray-500 border-b border-gray-100">
                <th className="px-4 py-3 font-medium">반품 ID</th>
                <th className="px-4 py-3 font-medium">주문</th>
                <th className="px-4 py-3 font-medium">항목</th>
                <th className="px-4 py-3 font-medium">신청자</th>
                <th className="px-4 py-3 font-medium">사유</th>
                <th className="px-4 py-3 font-medium">상태</th>
                <th className="px-4 py-3 font-medium">신청 시각</th>
                <th className="px-4 py-3 font-medium">처리</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <ReturnRow
                  key={row.id}
                  row={row}
                  busy={busyId === row.id}
                  onApprove={() => handleApprove(row)}
                  onReject={() => { setRejectTarget(row); setError(''); }}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* 반품 거부 사유 모달 */}
      {rejectTarget && (
        <RejectModal
          target={rejectTarget}
          onClose={() => setRejectTarget(null)}
          onDone={async () => { setRejectTarget(null); await load(); }}
        />
      )}
    </div>
  );
};

interface ReturnRowProps {
  row: ReturnRequest;
  busy: boolean;
  onApprove: () => void;
  onReject: () => void;
}

const ReturnRow = ({ row, busy, onApprove, onReject }: ReturnRowProps) => {
  const status = returnStatusStyle(row.status);
  const pending = row.status === 'REQUESTED'; // 승인·거부는 접수 상태에서만

  return (
    <tr className="border-b border-gray-50 last:border-0">
      <td className="px-4 py-3 text-gray-900 font-medium">#{row.id}</td>
      <td className="px-4 py-3 text-gray-700">#{row.orderId}</td>
      <td className="px-4 py-3 text-gray-700">#{row.orderItemId}</td>
      <td className="px-4 py-3 text-gray-700">{row.userId}</td>
      <td className="px-4 py-3 text-gray-700 max-w-[220px]">
        <span className="block truncate" title={row.reason}>{row.reason}</span>
        {row.rejectReason && (
          <span className="block truncate text-[11px] text-red-500" title={row.rejectReason}>
            거부: {row.rejectReason}
          </span>
        )}
      </td>
      <td className="px-4 py-3">
        <span className="text-[11px] font-bold px-2 py-0.5 rounded-full text-white"
          style={{ background: status.color }}>{status.text}</span>
      </td>
      <td className="px-4 py-3 text-gray-400">{formatDateTime(row.requestedAt)}</td>
      <td className="px-4 py-3">
        {pending ? (
          <div className="flex items-center gap-1.5">
            <button onClick={onApprove} disabled={busy}
              className="h-7 px-2.5 text-[11px] font-medium text-white bg-emerald-500 rounded-lg border-none disabled:opacity-60">
              {busy ? '처리 중...' : '승인'}
            </button>
            <button onClick={onReject} disabled={busy}
              className="h-7 px-2.5 text-[11px] font-medium text-red-500 border border-red-200 rounded-lg hover:bg-red-50 bg-white transition-colors disabled:opacity-60">
              거부
            </button>
          </div>
        ) : (
          <span className="text-[12px] text-gray-400">{formatDateTime(row.processedAt) || '-'}</span>
        )}
      </td>
    </tr>
  );
};

interface RejectModalProps {
  target: ReturnRequest;
  onClose: () => void;
  onDone: () => Promise<void>;
}

const RejectModal = ({ target, onClose, onDone }: RejectModalProps) => {
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    if (!reason.trim()) { setError('거부 사유를 입력해주세요.'); return; }
    setSubmitting(true);
    try {
      await rejectReturn(target.id, reason.trim());
      await onDone();
    } catch (err) {
      setError(errorDetail(err, '반품 거부에 실패했습니다.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[300] flex items-center justify-center bg-black/40 px-4"
      onClick={() => !submitting && onClose()}>
      <div className="bg-white rounded-2xl p-5 w-full max-w-[400px]" onClick={(e) => e.stopPropagation()}>
        <h2 className="text-[15px] font-bold text-gray-900 mb-1 m-0">반품 거부</h2>
        <p className="text-[13px] text-gray-500 mb-4 mt-1">
          반품 #{target.id} · 주문 #{target.orderId}
        </p>
        <label className="field-label">거부 사유 *</label>
        <textarea
          value={reason}
          onChange={(e) => { setReason(e.target.value); setError(''); }}
          placeholder="거부 사유를 입력하세요"
          rows={3}
          maxLength={300}
          className="input-field mt-1.5"
          style={{ resize: 'vertical' }}
        />
        {error && <div className="error-box mt-2">{error}</div>}
        <div className="flex justify-end gap-2 mt-4">
          <button onClick={onClose} disabled={submitting}
            className="h-10 px-4 text-sm font-medium text-gray-600 bg-white border border-gray-200 rounded-[10px]">
            닫기
          </button>
          <button onClick={submit} disabled={submitting}
            className="h-10 px-5 text-white text-sm font-semibold rounded-[10px] border-none bg-red-500 disabled:opacity-70">
            {submitting ? '처리 중...' : '거부 확정'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default AdminReturnPage;
