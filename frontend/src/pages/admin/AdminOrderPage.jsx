import { useState, useEffect } from 'react';
import { getAllOrders, getSellerOrders } from '../../api/order';
import useAuthStore from '../../store/authStore';

const formatPrice = (p) =>
  new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(p);

const STATUS_LABEL = {
  PENDING:   { text: '결제 대기', color: '#f59e0b' },
  CONFIRMED: { text: '주문 확정', color: '#22c55e' },
  CANCELLED: { text: '취소됨',   color: '#9ca3af' },
};

const AdminOrderPage = () => {
  const { role } = useAuthStore();
  const isAdmin = role === 'ADMIN';

  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetch = isAdmin ? getAllOrders : getSellerOrders;
    fetch(0, 50)
      .then((res) => setOrders(res.data.content ?? []))
      .catch(() => setError('주문 목록을 불러오지 못했습니다.'))
      .finally(() => setLoading(false));
  }, []);

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

      {error && <div className="error-box mb-4">{error}</div>}

      {orders.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-24 text-center">
          <p className="text-gray-400 text-[15px] m-0">주문 내역이 없습니다.</p>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          {orders.map((order) => {
            const status = STATUS_LABEL[order.status] ?? { text: order.status, color: '#6b7280' };
            return (
              <div key={order.id} className="bg-white border border-gray-100 rounded-2xl p-5">
                {/* 헤더 */}
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <span className="text-[12px] font-bold px-2 py-0.5 rounded-full text-white"
                      style={{ background: status.color }}>{status.text}</span>
                    <span className="text-[12px] text-gray-400">{formatDate(order.createdAt)}</span>
                  </div>
                  <span className="text-[12px] text-gray-300">#{order.id}</span>
                </div>

                {/* 배송 정보 */}
                <div className="text-[12px] text-gray-500 mb-3">
                  {order.receiver} · {order.phone} · {order.address}
                </div>

                {/* 상품 목록 */}
                <div className="flex flex-col gap-2 mb-3">
                  {(order.items ?? []).map((item) => (
                    <div key={item.id} className="flex items-center justify-between text-[13px]">
                      <span className="text-gray-700 truncate flex-1 mr-2">
                        {item.productName}
                        {item.status === 'CANCELLED' && (
                          <span className="ml-2 text-[11px] text-red-500 bg-red-50 px-1.5 py-0.5 rounded">취소</span>
                        )}
                        {isAdmin && item.sellerId && (
                          <span className="ml-2 text-[11px] text-emerald-600">판매자 #{item.sellerId}</span>
                        )}
                        {isAdmin && !item.sellerId && (
                          <span className="ml-2 text-[11px] text-amber-600">플랫폼</span>
                        )}
                      </span>
                      <span className="text-gray-400 shrink-0">× {item.quantity}</span>
                      <span className="text-gray-900 font-medium shrink-0 ml-3">
                        {formatPrice(item.subtotal)}
                      </span>
                    </div>
                  ))}
                </div>

                <div className="h-px bg-gray-100 mb-3" />
                <div className="flex justify-between">
                  <span className="text-[13px] text-gray-500">
                    {isAdmin ? '총 결제금액' : '내 상품 합계'}
                  </span>
                  <span className="text-[15px] font-bold text-gray-900">{formatPrice(order.totalPrice)}</span>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default AdminOrderPage;
