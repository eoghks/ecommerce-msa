import { useState, useEffect, useRef } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import type { AxiosError } from 'axios';
import { confirmPayment } from '../../api/payment';
import { toOrderId } from '../../utils/pgOrderId';
import useCartStore from '../../store/cartStore';
import type { ApiErrorResponse, Payment } from '../../types';

const formatPrice = (price: number) =>
  new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(price);

/**
 * 결제 성공 리다이렉트 (V1.1-6, payment-foundation §5).
 * 토스 결제위젯이 붙여준 (paymentKey, orderId, amount)로 서버 최종 승인을 호출한다.
 * 이 화면에 도달했다는 것만으로는 결제가 끝난 것이 아니다 — 서버 승인이 성공해야 주문이 확정된다.
 * 금액·소유자·멱등 검증은 모두 서버가 하며 실패 사유(detail)를 그대로 보여준다.
 */
const PaymentSuccessPage = () => {
  const [params] = useSearchParams();
  const [payment, setPayment] = useState<Payment | null>(null);
  const [error, setError] = useState('');
  const [confirming, setConfirming] = useState(true);
  const clearCart = useCartStore((s) => s.clear);

  // 승인은 1회만 — StrictMode 이중 실행으로 재승인(409)이 뜨지 않게 막는다
  const requestedRef = useRef(false);

  useEffect(() => {
    if (requestedRef.current) return;
    requestedRef.current = true;

    const paymentKey = params.get('paymentKey');
    // M-05: PG 주문번호는 시도마다 달라 서버가 재생성할 수 없다 — 받은 값을 그대로 전달한다
    const pgOrderId = params.get('orderId');
    const orderId = toOrderId(pgOrderId);
    const amount = Number(params.get('amount'));
    if (!paymentKey || !pgOrderId || orderId === null || !Number.isFinite(amount) || amount <= 0) {
      setError('결제 결과 정보가 올바르지 않습니다. 주문 내역에서 결제 상태를 확인해주세요.');
      setConfirming(false);
      return;
    }

    confirmPayment({ paymentKey, pgOrderId, orderId, amount })
      .then((res) => {
        setPayment(res.data);
        // 결제가 확정된 뒤에 장바구니를 비운다 — 승인 실패 시 담은 상품이 그대로 남는다
        return clearCart().catch(() => undefined);
      })
      .catch((err) => setError((err as AxiosError<ApiErrorResponse>).response?.data?.detail
        || '결제 승인에 실패했습니다. 주문 내역에서 결제 상태를 확인해주세요.'))
      .finally(() => setConfirming(false));
  }, [params, clearCart]);

  if (confirming) {
    return (
      <div className="flex flex-col items-center gap-4 py-32">
        <div className="w-7 h-7 rounded-full animate-spin border-[3px] border-gray-200 border-t-brand-600" />
        <p className="text-[13px] text-gray-500 m-0">결제를 확인하고 있습니다. 창을 닫지 마세요.</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="max-w-[520px] mx-auto flex flex-col items-center gap-4 py-24 text-center">
        <h1 className="text-xl font-bold text-gray-900 m-0">결제를 완료하지 못했습니다</h1>
        <div className="error-box w-full text-left">{error}</div>
        <Link to="/orders" className="btn-brand-fill no-underline text-sm">주문 내역 확인</Link>
      </div>
    );
  }

  return (
    <div className="max-w-[520px] mx-auto flex flex-col items-center gap-4 py-24 text-center">
      <div className="w-12 h-12 rounded-full bg-green-50 flex items-center justify-center">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#22c55e" strokeWidth="2.5"
          strokeLinecap="round" strokeLinejoin="round">
          <polyline points="20 6 9 17 4 12" />
        </svg>
      </div>
      <h1 className="text-xl font-bold text-gray-900 m-0">결제가 완료되었습니다</h1>
      {payment && (
        <p className="text-[14px] text-gray-600 m-0">
          주문 #{payment.orderId} · {formatPrice(payment.amount)}
        </p>
      )}
      <p className="text-[13px] text-gray-400 m-0">재고 확인 후 주문이 확정됩니다.</p>
      <div className="flex gap-2 mt-2">
        <Link to="/products" className="h-10 px-4 flex items-center text-sm font-medium text-gray-600 bg-white border border-gray-200 rounded-[10px] no-underline">
          쇼핑 계속하기
        </Link>
        <Link to="/orders" className="btn-brand-fill no-underline text-sm">주문 내역 보기</Link>
      </div>
    </div>
  );
};

export default PaymentSuccessPage;
