import { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { getProduct } from '../../api/product';
import useCartStore from '../../store/cartStore';
import useAuthStore from '../../store/authStore';

const formatPrice = (price) =>
  new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(price);

const ProductDetailPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuthStore();
  const addItem = useCartStore((s) => s.addItem);

  const [product, setProduct] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [quantity, setQuantity] = useState(1);
  const [added, setAdded] = useState(false);

  useEffect(() => {
    setLoading(true);
    getProduct(id)
      .then((res) => setProduct(res.data))
      .catch(() => setError('상품을 불러오는 데 실패했습니다.'))
      .finally(() => setLoading(false));
  }, [id]);

  const handleQuantity = (delta) => {
    setQuantity((q) => Math.max(1, Math.min(product?.stock ?? 1, q + delta)));
  };

  const handleAddCart = () => {
    if (!isAuthenticated) {
      navigate('/login', { state: { from: `/products/${id}` } });
      return;
    }
    addItem(product, quantity);
    setAdded(true);
    setTimeout(() => setAdded(false), 2000);
  };

  /* 로딩 */
  if (loading) return (
    <div className="flex justify-center items-center py-32">
      <div className="w-7 h-7 rounded-full animate-spin border-[3px] border-gray-200 border-t-brand-600" />
    </div>
  );

  /* 에러 */
  if (error) return (
    <div className="max-w-[640px] mx-auto mt-10 px-4">
      <div className="error-box">{error}</div>
      <Link to="/products" className="text-brand-600 text-sm font-medium no-underline mt-4 inline-block">
        ← 상품 목록으로
      </Link>
    </div>
  );

  return (
    <div className="max-w-[900px] mx-auto px-4">
      {/* 이전 페이지 */}
      <Link to="/products" className="inline-flex items-center gap-1 text-[13px] text-gray-500 no-underline hover:text-gray-700 mb-6">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
          <polyline points="15 18 9 12 15 6"/>
        </svg>
        상품 목록
      </Link>

      <div className="flex flex-col md:flex-row gap-8 lg:gap-12">
        {/* 이미지 */}
        <div className="w-full md:w-[380px] shrink-0">
          <div className="w-full aspect-square rounded-2xl bg-gray-100 flex items-center justify-center overflow-hidden border border-gray-200">
            {product.imageUrl ? (
              <img src={product.imageUrl} alt={product.name} className="w-full h-full object-cover" />
            ) : (
              <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="#d1d5db" strokeWidth="1.5">
                <rect x="3" y="3" width="18" height="18" rx="2" />
                <circle cx="8.5" cy="8.5" r="1.5" />
                <polyline points="21 15 16 10 5 21" />
              </svg>
            )}
          </div>
        </div>

        {/* 정보 */}
        <div className="flex flex-col gap-4 flex-1">
          {/* 카테고리 + 상품명 */}
          {product.categoryName && (
            <span className="text-[12px] font-semibold text-brand-600 uppercase tracking-wide">
              {product.categoryName}
            </span>
          )}
          <h1 className="text-xl sm:text-2xl font-bold text-gray-900 m-0 leading-snug">
            {product.name}
          </h1>

          {/* 가격 */}
          <p className="text-2xl sm:text-3xl font-bold text-gray-900 m-0">
            {formatPrice(product.price)}
          </p>

          {/* 재고 */}
          <span className={`text-sm font-medium ${product.stock > 0 ? 'text-green-500' : 'text-red-400'}`}>
            {product.stock > 0 ? `재고 ${product.stock}개 남음` : '품절'}
          </span>

          {/* 구분선 */}
          <div className="h-px bg-gray-100" />

          {/* 설명 */}
          {product.description && (
            <p className="text-[14px] text-gray-600 leading-relaxed m-0 whitespace-pre-line">
              {product.description}
            </p>
          )}

          {/* 수량 + 장바구니 */}
          {product.stock > 0 && (
            <div className="flex flex-col gap-3 mt-2">
              {/* 수량 조절 */}
              <div className="flex items-center gap-3">
                <span className="text-[13px] font-medium text-gray-700 w-12">수량</span>
                <div className="flex items-center border border-gray-200 rounded-lg overflow-hidden">
                  <button
                    onClick={() => handleQuantity(-1)}
                    disabled={quantity <= 1}
                    className="w-9 h-9 flex items-center justify-center text-gray-500 hover:bg-gray-50 disabled:opacity-30 transition-colors bg-transparent border-none text-lg"
                  >−</button>
                  <span className="w-10 text-center text-sm font-semibold text-gray-900">{quantity}</span>
                  <button
                    onClick={() => handleQuantity(1)}
                    disabled={quantity >= product.stock}
                    className="w-9 h-9 flex items-center justify-center text-gray-500 hover:bg-gray-50 disabled:opacity-30 transition-colors bg-transparent border-none text-lg"
                  >+</button>
                </div>
              </div>

              {/* 장바구니 버튼 */}
              <button
                onClick={handleAddCart}
                className="h-12 px-6 text-white text-[15px] font-semibold rounded-[10px] border-none transition-all duration-150 flex items-center justify-center gap-2"
                style={{ background: added ? '#22c55e' : 'linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)' }}
              >
                {added ? (
                  <>
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
                      <polyline points="20 6 9 17 4 12"/>
                    </svg>
                    담겼습니다!
                  </>
                ) : (
                  <>
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/>
                      <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"/>
                    </svg>
                    장바구니 담기
                  </>
                )}
              </button>

              {!isAuthenticated && (
                <p className="text-[12px] text-gray-400 m-0 text-center">
                  장바구니는 로그인 후 이용 가능합니다.
                </p>
              )}
            </div>
          )}

          {/* 등록일 */}
          {product.createdAt && (
            <p className="text-[12px] text-gray-400 m-0 mt-auto">
              등록일 · {new Date(product.createdAt).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })}
            </p>
          )}
        </div>
      </div>
    </div>
  );
};

export default ProductDetailPage;
