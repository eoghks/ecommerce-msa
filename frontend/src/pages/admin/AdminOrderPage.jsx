import { useState, useEffect } from 'react';
import { getAllOrders, getSellerOrders, cancelOrderItem, updateDeliveryStatus } from '../../api/order';
import useAuthStore from '../../store/authStore';

const formatPrice = (p) =>
  new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(p);

const STATUS_LABEL = {
  PENDING:             { text: '결제 대기',   color: '#f59e0b' },
  CONFIRMED:           { text: '주문 확정',   color: '#22c55e' },
  PARTIALLY_CANCELLED: { text: '부분 취소',   color: '#f97316' },
  CANCELLED:           { text: '취소됨',     color: '#9ca3af' },
};

// V1.1-3: 배송 진행 상태 라벨 + 전이 순서
const DELIVERY_LABEL = {
  PREPARING: { text: '배송 준비중', color: '#6366f1' },
  SHIPPING:  { text: '배송중',     color: '#0ea5e9' },
  DELIVERED: { text: '배송완료',   color: '#22c55e' },
};
// 현재 상태에서 전진 가능한 다음 상태 (없으면 완료)
const NEXT_DELIVERY = { PREPARING: 'SHIPPING', SHIPPING: 'DELIVERED' };
const DELIVERABLE_STATUSES = ['CONFIRMED', 'PARTIALLY_CANCELLED'];

const AdminOrderPage = () => {
  const { role } = useAuthStore();
  const isAdmin = role === 'ADMIN';

  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // 취소 모달 상태
  const [cancelTarget, setCancelTarget] = useState(null); // { orderId, itemId, productName }
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const load = () => {
    const fetch = isAdmin ? getAllOrders : getSellerOrders;
    return fetch(0, 50)
      .then((res) => setOrders(res.data.content ?? []))
      .catch(() => setError('주문 목록을 불러오지 못했습니다.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const openCancel = (orderId, item) => {
    setCancelTarget({ orderId, itemId: item.id, productName: item.productName });
    setReason('');
    setError('');
  };

  const submitCancel = async () => {
    if (!reason.trim()) { setError('취소 사유를 입력해주세요.'); return; }
    setSubmitting(true);
    try {
      await cancelOrderItem(cancelTarget.orderId, cancelTarget.itemId, reason.trim());
      setCancelTarget(null);
      await load();
    } catch (err) {
      setError(err.response?.data?.detail || '항목 취소에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  // V1.1-3: 배송상태 전진
  const [deliveryBusy, setDeliveryBusy] = useState(null); // 처리 중인 orderId
  const advanceDelivery = async (orderId, next) => {
    setDeliveryBusy(orderId);
    setError('');
    try {
      await updateDeliveryStatus(orderId, next);
      await load();
    } catch (err) {
      setError(err.response?.data?.detail || '배송상태 변경에 실패했습니다.');
    } finally {
      setDeliveryBusy(null);
    }
  };

  const formatDate = (d) =>
    d ? new Date(d).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' }) : '';

  if (loading) {
    return (
      <div className="flex justify-center items-center py-32">
        <div className="w-7 h-7 rounded-full animate-spin border-[3px] border-gray-200 border-t-brand-600" />
      </div>
    );
  }

  return (
    <div className="max-w-[860px] mx-auto">
      <h1 className="text-xl font-bold text-gray-900 mb-6 m-0">
        {isAdmin ? '주문 관리 (전체)' : '내 상품 주문'}
      </h1>

      {error && !cancelTarget && <div className="error-box mb-4">{error}</div>}

      {orders.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-24 text-center">
          <p className="text-gray-400 text-[15px] m-0">주문 내역이 없습니다.</p>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          {orders.map((order) => {
            const status = STATUS_LABEL[order.status] ?? { text: order.status, color: '#6b7280' };
            const deliverable = DELIVERABLE_STATUSES.includes(order.status);
            const delivery = deliverable ? DELIVERY_LABEL[order.deliveryStatus] : null;
            const nextDelivery = deliverable ? NEXT_DELIVERY[order.deliveryStatus] : null;
            return (
              <div key={order.id} className="bg-white border border-gray-100 rounded-2xl p-5">
                {/* 헤더 */}
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <span className="text-[12px] font-bold px-2 py-0.5 rounded-full text-white"
                      style={{ background: status.color }}>{status.text}</span>
                    {/* V1.1-3: 배송상태 뱃지 */}
                    {delivery && (
                      <span className="text-[12px] font-bold px-2 py-0.5 rounded-full text-white"
                        style={{ background: delivery.color }}>{delivery.text}</span>
                    )}
                    <span className="text-[12px] text-gray-400">{formatDate(order.createdAt)}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    {/* V1.1-3: 배송상태 전진 컨트롤 — 다음 단계만 노출 */}
                    {nextDelivery && (
                      <button onClick={() => advanceDelivery(order.id, nextDelivery)}
                        disabled={deliveryBusy === order.id}
                        className="h-7 px-2.5 text-[11px] font-medium text-white rounded-lg border-none disabled:opacity-60"
                        style={{ background: DELIVERY_LABEL[nextDelivery].color }}>
                        {deliveryBusy === order.id ? '변경 중...' : `${DELIVERY_LABEL[nextDelivery].text}(으)로`}
                      </button>
                    )}
                    <span className="text-[12px] text-gray-300">#{order.id}</span>
                  </div>
                </div>

                {/* 배송 정보 */}
                <div className="text-[12px] text-gray-500 mb-3">
                  {order.receiver} · {order.phone} · {order.address}
                </div>

                {/* 상품 목록 */}
                <div className="flex flex-col gap-2 mb-3">
                  {(order.items ?? []).map((item) => {
                    const cancelled = item.status === 'CANCELLED';
                    return (
                      <div key={item.id} className="flex items-center justify-between text-[13px] gap-2">
                        <div className="flex-1 min-w-0">
                          <span className={`truncate ${cancelled ? 'text-gray-400 line-through' : 'text-gray-700'}`}>
                            {item.productName}
                          </span>
                          {cancelled && (
                            <span className="ml-2 text-[11px] text-red-500 bg-red-50 px-1.5 py-0.5 rounded no-underline">
                              취소{item.cancelReason ? ` · ${item.cancelReason}` : ''}
                            </span>
                          )}
                          {isAdmin && item.sellerId && (
                            <span className="ml-2 text-[11px] text-emerald-600">판매자 #{item.sellerId}</span>
                          )}
                          {isAdmin && !item.sellerId && (
                            <span className="ml-2 text-[11px] text-amber-600">플랫폼</span>
                          )}
                        </div>
                        <span className="text-gray-400 shrink-0">× {item.quantity}</span>
                        <span className={`font-medium shrink-0 ${cancelled ? 'text-gray-400' : 'text-gray-900'}`}>
                          {formatPrice(item.subtotal)}
                        </span>
                        {/* 취소 버튼 — 살아있는 항목만 */}
                        {!cancelled && (
                          <button onClick={() => openCancel(order.id, item)}
                            className="shrink-0 h-7 px-2.5 text-[11px] font-medium text-red-500 border border-red-200 rounded-lg hover:bg-red-50 bg-white transition-colors">
                            취소
                          </button>
                        )}
                      </div>
                    );
                  })}
                </div>

                <div className="h-px bg-gray-100 mb-3" />
                <div className="flex justify-between">
                  <span className="text-[13px] text-gray-500">
                    {isAdmin ? '결제금액(유효)' : '내 상품 합계'}
                  </span>
                  <span className="text-[15px] font-bold text-gray-900">{formatPrice(order.totalPrice)}</span>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* 항목 취소 사유 모달 */}
      {cancelTarget && (
        <div className="fixed inset-0 z-[300] flex items-center justify-center bg-black/40 px-4"
          onClick={() => !submitting && setCancelTarget(null)}>
          <div className="bg-white rounded-2xl p-5 w-full max-w-[400px]" onClick={(e) => e.stopPropagation()}>
            <h2 className="text-[15px] font-bold text-gray-900 mb-1 m-0">항목 취소</h2>
            <p className="text-[13px] text-gray-500 mb-4 mt-1">{cancelTarget.productName}</p>
            <label className="field-label">취소 사유 *</label>
            <textarea
              value={reason}
              onChange={(e) => { setReason(e.target.value); setError(''); }}
              placeholder="취소 사유를 입력하세요"
              rows={3}
              className="input-field mt-1.5"
              style={{ resize: 'vertical' }}
            />
            {error && <div className="error-box mt-2">{error}</div>}
            <div className="flex justify-end gap-2 mt-4">
              <button onClick={() => setCancelTarget(null)} disabled={submitting}
                className="h-10 px-4 text-sm font-medium text-gray-600 bg-white border border-gray-200 rounded-[10px]">
                닫기
              </button>
              <button onClick={submitCancel} disabled={submitting}
                className="h-10 px-5 text-white text-sm font-semibold rounded-[10px] border-none bg-red-500 disabled:opacity-70">
                {submitting ? '취소 중...' : '취소 확정'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminOrderPage;
