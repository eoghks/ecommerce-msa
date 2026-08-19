import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import type { AxiosError } from 'axios';
import { getOrder } from '../../api/order';
import { newPgOrderId } from '../../utils/pgOrderId';
import {
  loadTossPayments,
  paymentRedirectUrls,
  tossClientKey,
  type TossPaymentWidgets,
} from '../../utils/tossPayments';
import type { ApiErrorResponse, Order } from '../../types';

const formatPrice = (price: number) =>
  new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(price);

// 위젯 렌더 대상 — SDK 가 selector 로 찾으므로 DOM 에 먼저 존재해야 한다
const METHODS_SELECTOR = 'toss-payment-methods';
const AGREEMENT_SELECTOR = 'toss-agreement';

// 사용자가 결제창을 닫았을 때 SDK 가 돌려주는 코드 — 오류로 취급하지 않는다
const USER_CANCEL_CODE = 'USER_CANCEL';

/** 결제 금액 — 서버가 계산한 실결제액(payableAmount)이 기준 */
const payableOf = (order: Order): number => order.payableAmount ?? order.totalPrice;

/** 주문명 — "첫 상품명 외 N건" (PG 표시용, 100자 제한) */
const orderNameOf = (order: Order): string => {
  const items = order.items ?? [];
  const first = items[0]?.productName ?? '주문';
  const name = items.length > 1 ? `${first} 외 ${items.length - 1}건` : first;
  return name.slice(0, 100);
};

/** SDK 거절 응답에서 코드만 안전하게 꺼낸다(형태가 보장되지 않아 좁혀서 읽는다) */
const sdkErrorCode = (err: unknown): string => {
  if (typeof err === 'object' && err !== null && 'code' in err) {
    const { code } = err as { code?: unknown };
    if (typeof code === 'string') return code;
  }
  return '';
};

const sdkErrorMessage = (err: unknown): string => {
  if (err instanceof Error && err.message) return err.message;
  if (typeof err === 'object' && err !== null && 'message' in err) {
    const { message } = err as { message?: unknown };
    if (typeof message === 'string' && message) return message;
  }
  return '결제 요청에 실패했습니다. 잠시 후 다시 시도해주세요.';
};

/**
 * 결제 화면 (V1.1-6, payment-foundation §5).
 * PAYMENT_PENDING 주문에 토스 결제위젯을 띄우고, 성공하면 SDK 가 /payments/success 로 리다이렉트한다.
 * 최종 승인은 서버(/api/v1/payments/confirm)가 하며 이 화면은 승인을 직접 확정하지 않는다.
 * 주문서에서 넘어오는 경로와 "결제 계속하기"(주문 내역) 경로가 이 화면을 공유한다.
 */
const PaymentPage = () => {
  const { orderId } = useParams();
  const navigate = useNavigate();

  const [order, setOrder] = useState<Order | null>(null);
  const [loading, setLoading] = useState(true);
  const [widgetReady, setWidgetReady] = useState(false);
  const [requesting, setRequesting] = useState(false);
  const [error, setError] = useState('');

  const widgetsRef = useRef<TossPaymentWidgets | null>(null);
  // StrictMode 이중 실행·재렌더 시 위젯이 두 번 그려지는 것을 막는다
  const initializedRef = useRef(false);

  useEffect(() => {
    const id = Number(orderId);
    if (!Number.isInteger(id) || id <= 0) {
      setError('주문 번호가 올바르지 않습니다.');
      setLoading(false);
      return;
    }
    // 주문과 로딩 해제를 같은 콜백에서 반영한다 — 위젯 렌더 대상 DOM 이 그려진 뒤 효과가 돌게 하려면
    // "주문 있음 + 로딩 끝" 이 한 번의 렌더로 커밋돼야 한다
    getOrder(id)
      .then((res) => {
        setOrder(res.data);
        setLoading(false);
      })
      .catch((err) => {
        setError((err as AxiosError<ApiErrorResponse>).response?.data?.detail
          || '주문 정보를 불러오지 못했습니다.');
        setLoading(false);
      });
  }, [orderId]);

  // 결제 대기 주문에만 위젯을 띄운다 — 이미 결제·취소된 주문은 안내만 표시
  const payable = order && order.status === 'PAYMENT_PENDING' ? payableOf(order) : 0;

  useEffect(() => {
    // loading 중에는 위젯 컨테이너가 DOM 에 없다 — selector 조회 실패를 막기 위해 렌더 후에만 초기화한다
    if (loading || !order || payable <= 0 || initializedRef.current) return;
    const clientKey = tossClientKey();
    if (!clientKey) {
      setError('결제 설정(VITE_TOSS_CLIENT_KEY)이 없습니다. 관리자에게 문의해주세요.');
      return;
    }
    initializedRef.current = true;

    loadTossPayments()
      .then(async (sdk) => {
        const widgets = sdk(clientKey).widgets({ customerKey: sdk.ANONYMOUS });
        await widgets.setAmount({ currency: 'KRW', value: payable });
        await Promise.all([
          widgets.renderPaymentMethods({ selector: `#${METHODS_SELECTOR}`, variantKey: 'DEFAULT' }),
          widgets.renderAgreement({ selector: `#${AGREEMENT_SELECTOR}`, variantKey: 'AGREEMENT' }),
        ]);
        widgetsRef.current = widgets;
        setWidgetReady(true);
      })
      .catch((err: unknown) => {
        initializedRef.current = false;
        setError(sdkErrorMessage(err));
      });
  }, [loading, order, payable]);

  const handlePay = async () => {
    const widgets = widgetsRef.current;
    if (!widgets || !order) return;
    setRequesting(true);
    setError('');
    const { successUrl, failUrl } = paymentRedirectUrls();
    try {
      // 성공·실패 모두 SDK 가 리다이렉트하므로 이 아래는 실행되지 않는다
      await widgets.requestPayment({
        // M-05: 시도마다 새 PG 주문번호 — 실패 후 재결제가 주문번호 재사용으로 막히지 않게 한다
        orderId: newPgOrderId(order.id),
        orderName: orderNameOf(order),
        successUrl,
        failUrl,
        customerName: order.receiver,
      });
    } catch (err: unknown) {
      // 결제창 닫기는 정상 이탈 — 주문은 결제 대기로 남아 재시도할 수 있다
      if (sdkErrorCode(err) !== USER_CANCEL_CODE) setError(sdkErrorMessage(err));
      setRequesting(false);
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center py-32">
        <div className="w-7 h-7 rounded-full animate-spin border-[3px] border-gray-200 border-t-brand-600" />
      </div>
    );
  }

  // 주문을 못 읽은 경우(잘못된 번호·타인 주문·통신 실패) — 결제 화면을 띄우지 않는다
  if (!order) {
    return (
      <div className="max-w-[560px] mx-auto flex flex-col items-center gap-4 py-24 text-center">
        <div className="error-box w-full text-left">{error || '주문 정보를 찾을 수 없습니다.'}</div>
        <Link to="/orders" className="btn-brand-fill no-underline text-sm">주문 내역으로</Link>
      </div>
    );
  }

  // 결제 대기가 아닌 주문 — 결제 완료/취소/만료. 재결제 대상이 아니므로 안내만 한다
  if (order.status !== 'PAYMENT_PENDING') {
    return (
      <div className="max-w-[560px] mx-auto flex flex-col items-center gap-4 py-24 text-center">
        <p className="text-gray-500 text-[15px] m-0">
          이 주문은 결제 대기 상태가 아닙니다. (현재 상태: {order.status})
        </p>
        <Link to="/orders" className="btn-brand-fill no-underline text-sm">주문 내역으로</Link>
      </div>
    );
  }

  return (
    <div className="max-w-[560px] mx-auto">
      <h1 className="text-xl font-bold text-gray-900 mb-2 m-0">결제</h1>
      <p className="text-[13px] text-gray-500 mt-0 mb-6">
        결제를 완료하면 주문이 확정됩니다. 30분 안에 결제하지 않으면 주문이 자동 취소됩니다.
      </p>

      <section className="bg-white border border-gray-100 rounded-2xl p-5 mb-4">
        <div className="flex items-center justify-between">
          <span className="text-[13px] text-gray-500">주문 #{order.id}</span>
          <span className="text-[15px] font-bold text-gray-900">{formatPrice(payable)}</span>
        </div>
        <div className="h-px bg-gray-100 my-4" />
        <div className="flex flex-col gap-2">
          {(order.items ?? []).map((item) => (
            <div key={item.id} className="flex items-center justify-between text-[13px]">
              <span className="text-gray-700 truncate mr-2">{item.productName}</span>
              <span className="text-gray-400 shrink-0">× {item.quantity}</span>
            </div>
          ))}
        </div>
      </section>

      {/* 토스 결제위젯 — 결제수단·약관 영역 */}
      <div className="bg-white border border-gray-100 rounded-2xl overflow-hidden mb-4">
        <div id={METHODS_SELECTOR} />
        <div id={AGREEMENT_SELECTOR} />
      </div>

      {error && <div className="error-box mb-4">{error}</div>}

      <div className="flex gap-2">
        <button
          type="button"
          onClick={() => navigate('/orders')}
          disabled={requesting}
          className="h-13 px-5 text-[15px] font-medium text-gray-600 bg-white border border-gray-200 rounded-[10px] disabled:opacity-60"
        >
          나중에
        </button>
        <button
          type="button"
          onClick={handlePay}
          disabled={!widgetReady || requesting}
          className="h-13 flex-1 text-white text-[15px] font-semibold rounded-[10px] border-none transition-all duration-150 flex items-center justify-center gap-2 py-3 disabled:opacity-60"
          style={{ background: 'linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)' }}
        >
          {requesting
            ? <><span className="spinner" />결제창 여는 중...</>
            : `${formatPrice(payable)} 결제하기`}
        </button>
      </div>
    </div>
  );
};

export default PaymentPage;
