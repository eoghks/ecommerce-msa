import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import useCartStore from '../../store/cartStore';
import { createOrder } from '../../api/order';

const formatPrice = (price) =>
  new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(price);

const OrderPage = () => {
  const navigate = useNavigate();
  const { items, totalPrice, clear } = useCartStore();

  const [address, setAddress] = useState('');
  const [receiver, setReceiver] = useState('');
  const [phone, setPhone] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center gap-4 py-24 text-center">
        <p className="text-gray-400 text-[15px] m-0">주문할 상품이 없습니다.</p>
        <Link to="/products" className="btn-brand-fill no-underline text-sm">
          쇼핑 계속하기
        </Link>
      </div>
    );
  }

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!receiver.trim() || !phone.trim() || !address.trim()) {
      setError('수령인, 연락처, 배송지를 모두 입력해주세요.');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const orderItems = items.map((i) => ({
        productId: i.productId,
        productName: i.productName,
        price: i.price,
        quantity: i.quantity,
      }));
      await createOrder(orderItems);
      await clear();
      navigate('/orders', { state: { ordered: true } });
    } catch (err) {
      setError(err.response?.data?.message || '주문 처리 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-[720px] mx-auto">
      {/* 뒤로가기 */}
      <Link to="/cart" className="inline-flex items-center gap-1 text-[13px] text-gray-500 no-underline hover:text-gray-700 mb-6">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
          <polyline points="15 18 9 12 15 6" />
        </svg>
        장바구니
      </Link>

      <h1 className="text-xl font-bold text-gray-900 mb-6 m-0">주문 / 결제</h1>

      <form onSubmit={handleSubmit} className="flex flex-col gap-6">
        {/* 주문 상품 */}
        <section className="bg-white border border-gray-100 rounded-2xl p-5">
          <h2 className="text-[14px] font-semibold text-gray-700 mb-4 m-0">주문 상품</h2>
          <div className="flex flex-col gap-3">
            {items.map((item) => (
              <div key={item.productId} className="flex items-center justify-between gap-3">
                <div className="flex items-center gap-3 min-w-0">
                  <div className="w-10 h-10 shrink-0 rounded-lg bg-gray-100 overflow-hidden">
                    {item.imageUrl
                      ? <img src={item.imageUrl} alt={item.productName} className="w-full h-full object-cover" />
                      : <div className="w-full h-full bg-gray-200" />
                    }
                  </div>
                  <span className="text-[13px] text-gray-700 truncate">{item.productName}</span>
                  <span className="text-[12px] text-gray-400 shrink-0">× {item.quantity}</span>
                </div>
                <span className="text-[13px] font-semibold text-gray-900 shrink-0">
                  {formatPrice(item.price * item.quantity)}
                </span>
              </div>
            ))}
          </div>
          <div className="h-px bg-gray-100 my-4" />
          <div className="flex justify-between">
            <span className="text-[13px] text-gray-500">합계</span>
            <span className="text-[15px] font-bold text-gray-900">{formatPrice(totalPrice())}</span>
          </div>
        </section>

        {/* 배송 정보 */}
        <section className="bg-white border border-gray-100 rounded-2xl p-5">
          <h2 className="text-[14px] font-semibold text-gray-700 mb-4 m-0">배송 정보</h2>
          <div className="flex flex-col gap-3">
            <div className="flex flex-col gap-1.5">
              <label className="field-label">수령인</label>
              <input
                type="text" value={receiver} onChange={(e) => setReceiver(e.target.value)}
                placeholder="수령인 이름"
                className="input-field"
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="field-label">연락처</label>
              <input
                type="tel" value={phone} onChange={(e) => setPhone(e.target.value)}
                placeholder="010-0000-0000"
                className="input-field"
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="field-label">배송지</label>
              <input
                type="text" value={address} onChange={(e) => setAddress(e.target.value)}
                placeholder="도로명 주소를 입력하세요"
                className="input-field"
              />
            </div>
          </div>
        </section>

        {/* 결제 수단 (데모 고정) */}
        <section className="bg-white border border-gray-100 rounded-2xl p-5">
          <h2 className="text-[14px] font-semibold text-gray-700 mb-3 m-0">결제 수단</h2>
          <div className="flex items-center gap-2 text-[13px] text-gray-600">
            <div className="w-4 h-4 rounded-full border-[5px] border-brand-600" />
            무통장입금
          </div>
        </section>

        {error && <div className="error-box">{error}</div>}

        <button
          type="submit"
          disabled={loading}
          className="h-13 w-full text-white text-[15px] font-semibold rounded-[10px] border-none transition-all duration-150 flex items-center justify-center gap-2 py-3"
          style={{ background: 'linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)' }}
        >
          {loading
            ? <><span className="spinner" />처리 중...</>
            : `${formatPrice(totalPrice())} 주문 확정`}
        </button>
      </form>
    </div>
  );
};

export default OrderPage;
