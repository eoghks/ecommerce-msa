import { useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import useCartStore from '../../store/cartStore';
import useAuthStore from '../../store/authStore';

const formatPrice = (price) =>
  new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(price);

const CartPage = () => {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuthStore();
  const { items, loading, fetchCart, updateQuantity, removeItem, clear, totalPrice, totalCount } =
    useCartStore();

  useEffect(() => {
    fetchCart();
  }, []);

  const handleOrder = () => {
    if (!isAuthenticated) {
      navigate('/login', { state: { from: '/cart' } });
      return;
    }
    navigate('/order');
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center py-32">
        <div className="w-7 h-7 rounded-full animate-spin border-[3px] border-gray-200 border-t-brand-600" />
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center gap-4 py-24 text-center">
        <svg width="52" height="52" viewBox="0 0 24 24" fill="none" stroke="#d1d5db" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="9" cy="21" r="1" /><circle cx="20" cy="21" r="1" />
          <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6" />
        </svg>
        <p className="text-gray-400 text-[15px] m-0">장바구니가 비어 있습니다.</p>
        <Link to="/products" className="btn-brand-fill no-underline text-sm">
          쇼핑 계속하기
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-[860px] mx-auto">
      {/* 헤더 */}
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-bold text-gray-900 m-0">
          장바구니
          <span className="ml-2 text-sm font-normal text-gray-400">{totalCount()}개</span>
        </h1>
        <button
          onClick={clear}
          className="text-[12px] text-gray-400 hover:text-red-400 bg-transparent border-none transition-colors"
        >
          전체 삭제
        </button>
      </div>

      {/* 아이템 목록 */}
      <div className="flex flex-col gap-3 mb-6">
        {items.map((item) => (
          <CartItem
            key={item.productId}
            item={item}
            onUpdateQuantity={updateQuantity}
            onRemove={removeItem}
          />
        ))}
      </div>

      {/* 합계 + 주문 버튼 */}
      <div className="bg-gray-50 rounded-2xl p-5 flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <span className="text-[14px] text-gray-500">총 상품 금액</span>
          <span className="text-lg font-bold text-gray-900">{formatPrice(totalPrice())}</span>
        </div>
        <button
          onClick={handleOrder}
          className="h-12 w-full text-white text-[15px] font-semibold rounded-[10px] border-none transition-all duration-150"
          style={{ background: 'linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)' }}
        >
          {isAuthenticated ? `${formatPrice(totalPrice())} 주문하기` : '로그인 후 주문하기'}
        </button>
        {!isAuthenticated && (
          <p className="text-[12px] text-gray-400 m-0 text-center">
            비로그인 장바구니는 30일 동안 유지됩니다. 주문은 로그인 후 가능합니다.
          </p>
        )}
      </div>
    </div>
  );
};

/** 개별 장바구니 아이템 */
const CartItem = ({ item, onUpdateQuantity, onRemove }) => (
  <div className="flex items-center gap-4 bg-white border border-gray-100 rounded-2xl p-4">
    {/* 이미지 */}
    <div className="w-16 h-16 shrink-0 rounded-xl bg-gray-100 flex items-center justify-center overflow-hidden border border-gray-100">
      {item.imageUrl ? (
        <img src={item.imageUrl} alt={item.productName} className="w-full h-full object-cover" />
      ) : (
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#d1d5db" strokeWidth="1.5">
          <rect x="3" y="3" width="18" height="18" rx="2" /><polyline points="21 15 16 10 5 21" />
        </svg>
      )}
    </div>

    {/* 상품명 + 가격 */}
    <div className="flex-1 min-w-0">
      <p className="text-[14px] font-medium text-gray-900 m-0 truncate">{item.productName}</p>
      <p className="text-[13px] text-gray-500 m-0 mt-0.5">
        {new Intl.NumberFormat('ko-KR').format(item.price)}원
      </p>
    </div>

    {/* 수량 조절 */}
    <div className="flex items-center border border-gray-200 rounded-lg overflow-hidden shrink-0">
      <button
        onClick={() => onUpdateQuantity(item.productId, Math.max(1, item.quantity - 1))}
        disabled={item.quantity <= 1}
        className="w-8 h-8 flex items-center justify-center text-gray-500 hover:bg-gray-50 disabled:opacity-30 transition-colors bg-transparent border-none"
      >−</button>
      <span className="w-8 text-center text-sm font-semibold text-gray-900">{item.quantity}</span>
      <button
        onClick={() => onUpdateQuantity(item.productId, item.quantity + 1)}
        className="w-8 h-8 flex items-center justify-center text-gray-500 hover:bg-gray-50 transition-colors bg-transparent border-none"
      >+</button>
    </div>

    {/* 소계 */}
    <p className="text-[14px] font-bold text-gray-900 m-0 w-20 text-right shrink-0">
      {new Intl.NumberFormat('ko-KR').format(item.price * item.quantity)}원
    </p>

    {/* 삭제 */}
    <button
      onClick={() => onRemove(item.productId)}
      className="text-gray-300 hover:text-red-400 bg-transparent border-none transition-colors shrink-0 p-1"
    >
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
        <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
      </svg>
    </button>
  </div>
);

export default CartPage;
