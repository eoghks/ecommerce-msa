import { useState, useEffect } from 'react';
import { useLocation, Link } from 'react-router-dom';
import { getMyOrders } from '../../api/order';

const formatPrice = (price) =>
  new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(price);

const STATUS_LABEL = {
  PENDING: { text: '결제 대기', color: '#f59e0b' },
  PAID: { text: '결제 완료', color: '#3b82f6' },
  SHIPPING: { text: '배송 중', color: '#8b5cf6' },
  DELIVERED: { text: '배송 완료', color: '#22c55e' },
  CANCELLED: { text: '취소됨', color: '#9ca3af' },
};

const OrderListPage = () => {
  const location = useLocation();
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const justOrdered = location.state?.ordered;

  useEffect(() => {
    setLoading(true);
    getMyOrders(0, 20)
      .then((res) => setOrders(res.data.content ?? res.data ?? []))
      .catch(() => setError('주문 내역을 불러오는 데 실패했습니다.'))
      .finally(() => setLoading(false));
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
            <OrderCard key={order.id} order={order} />
          ))}
        </div>
      )}
    </div>
  );
};

const OrderCard = ({ order }) => {
  const status = STATUS_LABEL[order.status] ?? { text: order.status, color: '#6b7280' };
  const date = order.createdAt
    ? new Date(order.createdAt).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
    : '';

  return (
    <div className="bg-white border border-gray-100 rounded-2xl p-5">
      {/* 주문 헤더 */}
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <span className="text-[12px] font-bold px-2 py-0.5 rounded-full text-white"
            style={{ background: status.color }}>
            {status.text}
          </span>
          <span className="text-[12px] text-gray-400">{date}</span>
        </div>
        <span className="text-[12px] text-gray-300">#{order.id}</span>
      </div>

      {/* 상품 목록 */}
      <div className="flex flex-col gap-2 mb-3">
        {(order.items ?? []).map((item, idx) => (
          <div key={idx} className="flex items-center justify-between text-[13px]">
            <span className="text-gray-700 truncate flex-1 mr-2">{item.productName}</span>
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
    </div>
  );
};

export default OrderListPage;
