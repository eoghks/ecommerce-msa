import { useState } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import type { AxiosError } from 'axios';
import { cancelOrder } from '../../api/order';
import { toOrderId } from '../../utils/pgOrderId';
import type { ApiErrorResponse } from '../../types';

// PG 가 사유를 주지 않았을 때 표시할 기본 문구
const DEFAULT_FAIL_MESSAGE = '결제가 정상적으로 처리되지 않았습니다.';

/**
 * 결제 실패 리다이렉트 (V1.1-6, payment-foundation §5).
 * 승인 전 단계이므로 결제된 금액이 없고 주문은 결제 대기로 남아 있다 → 재시도 또는 즉시 취소를 제공한다.
 * 방치하면 30분 후 스케줄러가 주문을 자동 만료시킨다(§11.1).
 */
const PaymentFailPage = () => {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState('');

  const orderId = toOrderId(params.get('orderId'));
  const message = params.get('message') || DEFAULT_FAIL_MESSAGE;
  const code = params.get('code');

  const handleCancel = () => {
    if (orderId === null) return;
    setCancelling(true);
    setCancelError('');
    cancelOrder(orderId, '결제 실패')
      .then(() => navigate('/orders'))
      .catch((err) => setCancelError((err as AxiosError<ApiErrorResponse>).response?.data?.detail
        || '주문 취소에 실패했습니다.'))
      .finally(() => setCancelling(false));
  };

  return (
    <div className="max-w-[520px] mx-auto flex flex-col items-center gap-4 py-24 text-center">
      <div className="w-12 h-12 rounded-full bg-red-50 flex items-center justify-center">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#ef4444" strokeWidth="2.5"
          strokeLinecap="round" strokeLinejoin="round">
          <line x1="18" y1="6" x2="6" y2="18" />
          <line x1="6" y1="6" x2="18" y2="18" />
        </svg>
      </div>
      <h1 className="text-xl font-bold text-gray-900 m-0">결제에 실패했습니다</h1>
      <div className="error-box w-full text-left">{message}</div>
      {code && <p className="text-[12px] text-gray-400 m-0">오류 코드: {code}</p>}
      <p className="text-[13px] text-gray-500 m-0">
        결제된 금액은 없습니다. 30분 안에 결제하지 않으면 주문이 자동 취소됩니다.
      </p>

      {cancelError && <div className="error-box w-full text-left">{cancelError}</div>}

      <div className="flex gap-2 mt-2">
        {orderId !== null ? (
          <>
            <button type="button" onClick={handleCancel} disabled={cancelling}
              className="h-10 px-4 text-[13px] font-medium text-red-600 border border-red-200 rounded-[10px] bg-white hover:bg-red-50 transition-colors disabled:opacity-60">
              {cancelling ? '취소 중...' : '주문 취소'}
            </button>
            <Link to={`/payments/${orderId}`} className="btn-brand-fill no-underline text-sm">
              다시 결제하기
            </Link>
          </>
        ) : (
          <Link to="/orders" className="btn-brand-fill no-underline text-sm">주문 내역으로</Link>
        )}
      </div>
    </div>
  );
};

export default PaymentFailPage;
