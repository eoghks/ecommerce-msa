import { useState, useEffect } from 'react';
import { useLocation, Link } from 'react-router-dom';
import type { AxiosError } from 'axios';
import { getMyOrders, cancelOrder } from '../../api/order';
import { getMyReturns, requestReturn } from '../../api/return';
import { returnStatusStyle, blocksNewReturn } from '../../utils/returnStatus';
import type { ApiErrorResponse, Order, OrderItem, OrderStatus, ReturnRequest } from '../../types';

const formatPrice = (price: number) =>
  new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(price);

interface StatusStyle {
  text: string;
  color: string;
}

const STATUS_LABEL: Record<string, StatusStyle> = {
  PENDING:             { text: '결제 대기',   color: '#f59e0b' },
  CONFIRMED:           { text: '주문 확정',   color: '#22c55e' },
  PARTIALLY_CANCELLED: { text: '일부 취소',   color: '#f97316' },
  CANCELLED:           { text: '취소됨',     color: '#9ca3af' },
};

// V1.1-3: 배송 진행 상태 뱃지 라벨
const DELIVERY_LABEL: Record<string, StatusStyle> = {
  PREPARING: { text: '배송 준비중', color: '#6366f1' },
  SHIPPING:  { text: '배송중',     color: '#0ea5e9' },
  DELIVERED: { text: '배송완료',   color: '#22c55e' },
};

// 배송상태는 재고 차감된(확정/부분취소) 주문에서만 의미
const DELIVERABLE_STATUSES: OrderStatus[] = ['CONFIRMED', 'PARTIALLY_CANCELLED'];

// M-N3: 사용자가 취소 가능한 상태 (이미 전체취소된 CANCELLED 제외)
const CANCELLABLE_STATUSES: OrderStatus[] = ['PENDING', 'CONFIRMED', 'PARTIALLY_CANCELLED'];

// V1.1-1: 구매 확정(재고 차감 완료) 상태 — 리뷰 작성 가능
const REVIEWABLE_STATUSES: OrderStatus[] = ['CONFIRMED', 'PARTIALLY_CANCELLED'];

// V1.1-5: 내 반품 조회 페이지 크기 — 표시 중인 주문 항목의 반품 상태 매칭용
const RETURN_LOOKUP_SIZE = 100;

/** 주문 항목별 최신 반품 1건 매핑 (목록이 신청 최신순이므로 먼저 만난 건이 최신) */
const toReturnsByItem = (returns: ReturnRequest[]): Record<number, ReturnRequest> => {
  const byItem: Record<number, ReturnRequest> = {};
  returns.forEach((r) => {
    if (!byItem[r.orderItemId]) byItem[r.orderItemId] = r;
  });
  return byItem;
};

/** 반품 신청 모달 대상 */
interface ReturnTarget {
  orderId: number;
  itemId: number;
  productName: string;
}

const OrderListPage = () => {
  const location = useLocation();
  const [orders, setOrders] = useState<Order[]>([]);
  const [returnsByItem, setReturnsByItem] = useState<Record<number, ReturnRequest>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const justOrdered = location.state?.ordered;

  // V1.1-5: 반품 신청 모달 상태
  const [returnTarget, setReturnTarget] = useState<ReturnTarget | null>(null);

  const load = () => {
    setLoading(true);
    return Promise.all([
      getMyOrders(0, 20),
      // 반품 조회 실패는 주문 목록 표시를 막지 않는다(반품 뱃지만 미표시)
      getMyReturns(0, RETURN_LOOKUP_SIZE).catch(() => null),
    ])
      .then(([orderRes, returnRes]) => {
        setOrders(orderRes.data.content ?? []);
        setReturnsByItem(toReturnsByItem(returnRes?.data.content ?? []));
      })
      .catch(() => setError('주문 내역을 불러오는 데 실패했습니다.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const openReturn = (orderId: number, item: OrderItem) =>
    setReturnTarget({ orderId, itemId: item.id, productName: item.productName });

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
            <OrderCard key={order.id} order={order} returnsByItem={returnsByItem}
              onCancelled={load} onRequestReturn={openReturn} />
          ))}
        </div>
      )}

      {/* V1.1-5: 반품 신청 사유 모달 */}
      {returnTarget && (
        <ReturnRequestModal
          target={returnTarget}
          onClose={() => setReturnTarget(null)}
          onDone={async () => { setReturnTarget(null); await load(); }}
        />
      )}
    </div>
  );
};

interface OrderCardProps {
  order: Order;
  returnsByItem: Record<number, ReturnRequest>;
  onCancelled?: () => void;
  onRequestReturn: (orderId: number, item: OrderItem) => void;
}

const OrderCard = ({ order, returnsByItem, onCancelled, onRequestReturn }: OrderCardProps) => {
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState('');
  const status = STATUS_LABEL[order.status] ?? { text: order.status, color: '#6b7280' };
  const date = order.createdAt
    ? new Date(order.createdAt).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
    : '';
  const cancellable = CANCELLABLE_STATUSES.includes(order.status);
  const reviewable = REVIEWABLE_STATUSES.includes(order.status);
  const delivery = DELIVERABLE_STATUSES.includes(order.status)
    ? DELIVERY_LABEL[order.deliveryStatus ?? '']
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
          <OrderItemRow key={idx} item={item} reviewable={reviewable}
            delivered={order.deliveryStatus === 'DELIVERED'}
            itemReturn={returnsByItem[item.id]}
            onRequestReturn={() => onRequestReturn(order.id, item)} />
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

interface OrderItemRowProps {
  item: OrderItem;
  reviewable: boolean;
  delivered: boolean;
  itemReturn?: ReturnRequest;
  onRequestReturn: () => void;
}

// 주문 항목 1행 — 리뷰 쓰기(V1.1-1), 반품 뱃지·신청(V1.1-5)
const OrderItemRow = ({ item, reviewable, delivered, itemReturn, onRequestReturn }: OrderItemRowProps) => {
  const active = item.status === 'ACTIVE';
  const returnBadge = itemReturn ? returnStatusStyle(itemReturn.status) : null;
  // 반품 신청 가능: 배송완료 주문의 활성 항목 + 진행 중인 반품 없음(거부 건은 재신청 허용)
  const returnable = delivered && active && !(itemReturn && blocksNewReturn(itemReturn.status));

  return (
    <div className="flex items-center justify-between text-[13px]">
      <span className="text-gray-700 truncate flex-1 mr-2">{item.productName}</span>
      {/* V1.1-5: 반품 진행 상태 뱃지 */}
      {returnBadge && (
        <span className="text-[11px] font-bold px-2 py-0.5 rounded-full text-white shrink-0 mr-2"
          style={{ background: returnBadge.color }}>
          {returnBadge.text}
        </span>
      )}
      {/* V1.1-1: 구매 확정 + 활성 항목이면 리뷰 쓰기 (상세 페이지 리뷰 폼으로 이동) */}
      {reviewable && active && (
        <Link to={`/products/${item.productId}`}
          className="text-[12px] text-brand-600 no-underline font-medium shrink-0 mr-3 hover:underline">
          리뷰 쓰기
        </Link>
      )}
      {/* V1.1-5: 반품 신청 */}
      {returnable && (
        <button onClick={onRequestReturn}
          className="shrink-0 h-7 px-2.5 mr-3 text-[11px] font-medium text-orange-600 border border-orange-200 rounded-lg hover:bg-orange-50 bg-white transition-colors">
          반품 신청
        </button>
      )}
      <span className="text-gray-400 shrink-0">× {item.quantity}</span>
      <span className="text-gray-900 font-medium shrink-0 ml-3">
        {formatPrice(item.price * item.quantity)}
      </span>
    </div>
  );
};

interface ReturnRequestModalProps {
  target: ReturnTarget;
  onClose: () => void;
  onDone: () => Promise<void>;
}

// V1.1-5: 반품 신청 사유 입력 모달 — 자격 미충족(400)·중복(409)·권한(403) 시 서버 메시지 노출
const ReturnRequestModal = ({ target, onClose, onDone }: ReturnRequestModalProps) => {
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    if (!reason.trim()) { setError('반품 사유를 입력해주세요.'); return; }
    setSubmitting(true);
    try {
      await requestReturn(target.orderId, target.itemId, reason.trim());
      await onDone();
    } catch (err) {
      setError((err as AxiosError<ApiErrorResponse>).response?.data?.detail
        || '반품 신청에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[300] flex items-center justify-center bg-black/40 px-4"
      onClick={() => !submitting && onClose()}>
      <div className="bg-white rounded-2xl p-5 w-full max-w-[400px]" onClick={(e) => e.stopPropagation()}>
        <h2 className="text-[15px] font-bold text-gray-900 mb-1 m-0">반품 신청</h2>
        <p className="text-[13px] text-gray-500 mb-4 mt-1">{target.productName}</p>
        <label className="field-label">반품 사유 *</label>
        <textarea
          value={reason}
          onChange={(e) => { setReason(e.target.value); setError(''); }}
          placeholder="반품 사유를 입력하세요"
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
            className="h-10 px-5 text-white text-sm font-semibold rounded-[10px] border-none bg-orange-500 disabled:opacity-70">
            {submitting ? '신청 중...' : '반품 신청'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default OrderListPage;
