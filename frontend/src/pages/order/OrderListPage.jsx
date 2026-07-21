import { useState, useEffect } from 'react';
import { useLocation, Link } from 'react-router-dom';
import { getMyOrders, cancelOrder } from '../../api/order';

const formatPrice = (price) =>
  new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(price);

const STATUS_LABEL = {
  PENDING:             { text: '결제 대기',   color: '#f59e0b' },
  CONFIRMED:           { text: '주문 확정',   color: '#22c55e' },
  PARTIALLY_CANCELLED: { text: '일부 취소',   color: '#f97316' },
  CANCELLED:           { text: '취소됨',     color: '#9ca3af' },
};

// V1.1-3: 배송 진행 상태 뱃지 라벨
const DELIVERY_LABEL = {
  PREPARING: { text: '배송 준비중', color: '#6366f1' },
  SHIPPING:  { text: '배송중',     color: '#0ea5e9' },
  DELIVERED: { text: '배송완료',   color: '#22c55e' },
};

// 배송상태는 재고 차감된(확정/부분취소) 주문에서만 의미
const DELIVERABLE_STATUSES = ['CONFIRMED', 'PARTIALLY_CANCELLED'];

// M-N3: 사용자가 취소 가능한 상태 (이미 전체취소된 CANCELLED 제외)
const CANCELLABLE_STATUSES = ['PENDING', 'CONFIRMED', 'PARTIALLY_CANCELLED'];

// V1.1-1: 구매 확정(재고 차감 완료) 상태 — 리뷰 작성 가능
const REVIEWABLE_STATUSES = ['CONFIRMED', 'PARTIALLY_CANCELLED'];

const OrderListPage = () => {
  const location = useLocation();
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const justOrdered = location.state?.ordered;

  const load = () => {
    setLoading(true);
    return getMyOrders(0, 20)
      .then((res) => setOrders(res.data.content ?? res.data ?? []))
      .catch(() => setError('주문 내역을 불러오는 데 실패했습니다.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  if (loading) {
    return (
      <div className="flex justify-center items-center py-32">
        <div className="w-7 h-7 rounded-full animate-spin border-[3px] border-gray-200 border-t-brand-600" />
      </div>
    );
  }

  return (
    <div className="max-w-[720px] mx-auto">
      <h1 className="text-xl font-bold text-gray-900 mb-6 m-0">내 주문 내역</h1>

      {/* 주문 완료 안내 */}
      {justOrdered && (
        <div className="info-box mb-4 flex items-center gap-2">
          <span className="text-green-500 text-[6px]">●</span>
          주문이 완료되었습니다! 주문 내역을 확인하세요.
        </div>
      )}

      {error && <div className="error-box mb-4">{error}</div>}

      {orders.length === 0 ? (
        <div className="flex flex-col items-center gap-4 py-24 text-center">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#d1d5db" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
            <polyline points="14 2 14 8 20 8" />
          </svg>
          <p className="text-gray-400 text-[15px] m-0">주문 내역이 없습니다.</p>
          <Link to="/products" className="btn-brand-fill no-underline text-sm">
            쇼핑하러 가기
          </Link>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          {orders.map((order) => (
            <OrderCard key={order.id} order={order} onCancelled={load} />
          ))}
        </div>
      )}
    </div>
  );
};

const OrderCard = ({ order, onCancelled }) => {
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState('');
  const status = STATUS_LABEL[order.status] ?? { text: order.status, color: '#6b7280' };
  const date = order.createdAt
    ? new Date(order.createdAt).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
    : '';
  const cancellable = CANCELLABLE_STATUSES.includes(order.status);
  const reviewable = REVIEWABLE_STATUSES.includes(order.status);
  const delivery = DELIVERABLE_STATUSES.includes(order.status)
    ? DELIVERY_LABEL[order.deliveryStatus]
    : null;

  // M-N3: 주문 취소 — 사유 입력은 선택(비우면 서버 기본 사유)
  const handleCancel = () => {
    if (!window.confirm('이 주문을 취소하시겠습니까?')) return;
    const reason = window.prompt('취소 사유 (선택 — 비워두면 "고객 주문 취소")', '');
    if (reason === null) return; // 프롬프트 취소
    setCancelling(true);
    setCancelError('');
    cancelOrder(order.id, reason.trim() || undefined)
      .then(() => onCancelled?.())
      .catch(() => setCancelError('주문 취소에 실패했습니다.'))
      .finally(() => setCancelling(false));
  };

  return (
    <div className="bg-white border border-gray-100 rounded-2xl p-5">
      {/* 주문 헤더 */}
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <span className="text-[12px] font-bold px-2 py-0.5 rounded-full text-white"
            style={{ background: status.color }}>
            {status.text}
          </span>
          {/* V1.1-3: 배송상태 뱃지 */}
          {delivery && (
            <span className="text-[12px] font-bold px-2 py-0.5 rounded-full text-white"
              style={{ background: delivery.color }}>
              {delivery.text}
            </span>
          )}
          <span className="text-[12px] text-gray-400">{date}</span>
        </div>
        <span className="text-[12px] text-gray-300">#{order.id}</span>
      </div>

      {/* 상품 목록 */}
      <div className="flex flex-col gap-2 mb-3">
        {(order.items ?? []).map((item, idx) => (
          <div key={idx} className="flex items-center justify-between text-[13px]">
            <span className="text-gray-700 truncate flex-1 mr-2">{item.productName}</span>
            {/* V1.1-1: 구매 확정 + 활성 항목이면 리뷰 쓰기 (상세 페이지 리뷰 폼으로 이동) */}
            {reviewable && item.status === 'ACTIVE' && (
              <Link to={`/products/${item.productId}`}
                className="text-[12px] text-brand-600 no-underline font-medium shrink-0 mr-3 hover:underline">
                리뷰 쓰기
              </Link>
            )}
            <span className="text-gray-400 shrink-0">× {item.quantity}</span>
            <span className="text-gray-900 font-medium shrink-0 ml-3">
              {formatPrice(item.price * item.quantity)}
            </span>
          </div>
        ))}
      </div>

      {/* 합계 */}
      <div className="h-px bg-gray-100 mb-3" />
      <div className="flex justify-between">
        <span className="text-[13px] text-gray-500">총 결제금액</span>
        <span className="text-[15px] font-bold text-gray-900">
          {formatPrice(order.totalPrice ?? (order.items ?? []).reduce((s, i) => s + i.price * i.quantity, 0))}
        </span>
      </div>

      {/* M-N3: 주문 취소 버튼 — 취소 가능 상태에서만 노출 */}
      {cancellable && (
        <div className="mt-4 flex flex-col items-end gap-1">
          {cancelError && <span className="text-[12px] text-red-500">{cancelError}</span>}
          <button onClick={handleCancel} disabled={cancelling}
            className="h-9 px-4 text-[13px] font-medium text-red-600 border border-red-200 rounded-[10px] hover:bg-red-50 bg-white transition-colors disabled:opacity-60">
            {cancelling ? '취소 중...' : '주문 취소'}
          </button>
        </div>
      )}
    </div>
  );
};

export default OrderListPage;
