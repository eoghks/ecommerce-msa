import { useState, useEffect, useCallback } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { getMyWishlist } from '../../api/wishlist';
import useWishlistStore from '../../store/wishlistStore';

const PAGE_SIZE = 20;

const formatPrice = (price) =>
  new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(price);

const formatDate = (dateStr) => {
  if (!dateStr) return '';
  return new Date(dateStr).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' });
};

/* 찜 목록 항목 카드 */
const WishlistCard = ({ item, onRemove }) => {
  const banned = item.status === '판매중지';

  const handleRemove = async (e) => {
    e.preventDefault();
    e.stopPropagation();
    await onRemove(item.productId);
  };

  return (
    <Link to={`/products/${item.productId}`} className="no-underline group block">
      <div className="bg-white rounded-2xl overflow-hidden flex flex-col h-full transition-all duration-200 hover:shadow-[0_8px_32px_rgba(0,0,0,0.12)] hover:-translate-y-0.5"
        style={{ border: '1px solid #e5e7eb', boxShadow: '0 2px 8px rgba(0,0,0,0.06)' }}>

        <div className="relative w-full aspect-square overflow-hidden bg-gray-100">
          {item.imageUrl ? (
            <img
              src={item.imageUrl}
              alt={item.name}
              className="absolute inset-0 w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
              loading="lazy"
            />
          ) : (
            <div className="absolute inset-0 flex items-center justify-center bg-gradient-to-br from-gray-100 to-gray-200">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#9ca3af" strokeWidth="1.5">
                <rect x="3" y="3" width="18" height="18" rx="2" />
                <circle cx="8.5" cy="8.5" r="1.5" />
                <polyline points="21 15 16 10 5 21" />
              </svg>
            </div>
          )}

          {/* 판매중지 배지 */}
          {banned && (
            <div className="absolute inset-0 bg-black/40 flex items-center justify-center">
              <span className="bg-white/90 text-gray-700 text-xs font-bold px-3 py-1 rounded-full">판매중지</span>
            </div>
          )}

          {/* 찜 해제 버튼 */}
          <button
            onClick={handleRemove}
            title="찜 해제"
            aria-label="찜 해제"
            className="absolute top-3 right-3 w-9 h-9 rounded-full flex items-center justify-center bg-white/90 border-none shadow-md transition-transform duration-150 hover:scale-110"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="#ef4444" stroke="#ef4444"
              strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
            </svg>
          </button>
        </div>

        <div className="p-2.5 flex flex-col gap-0.5 flex-1">
          <p className="text-[13px] font-semibold text-gray-900 leading-snug line-clamp-2 m-0 flex-1">
            {item.name}
          </p>
          <p className="text-[15px] font-bold text-gray-900 m-0 mt-1">{formatPrice(item.price)}</p>
          <p className="text-[11px] text-gray-400 m-0 mt-0.5">{formatDate(item.createdAt)} 찜</p>
        </div>
      </div>
    </Link>
  );
};

const WishlistPage = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const removeWish = useWishlistStore((s) => s.remove);

  const [items, setItems] = useState([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const page = Number(searchParams.get('page') || 0);

  const fetchWishlist = useCallback(() => {
    setLoading(true);
    setError('');
    getMyWishlist({ page, size: PAGE_SIZE })
      .then((res) => {
        setItems(res.data.content ?? []);
        setTotalPages(res.data.totalPages ?? 0);
        setTotalElements(res.data.totalElements ?? 0);
      })
      .catch(() => setError('찜 목록을 불러오는 데 실패했습니다.'))
      .finally(() => setLoading(false));
  }, [page]);

  useEffect(() => { fetchWishlist(); }, [fetchWishlist]);

  /* 해제 → 목록에서 즉시 제거(낙관적), 실패 시 재조회로 복원 */
  const handleRemove = async (productId) => {
    const prev = items;
    setItems((list) => list.filter((i) => i.productId !== productId));
    setTotalElements((n) => Math.max(0, n - 1));
    try {
      await removeWish(productId);
    } catch (err) {
      // 낙관적 제거 원복 + 실패 사유 노출
      setItems(prev);
      setTotalElements((n) => n + 1);
      setError(err?.response?.data?.detail || '찜 해제에 실패했습니다.');
      fetchWishlist();
    }
  };

  const handlePage = (p) => {
    setSearchParams((prevParams) => {
      const next = new URLSearchParams(prevParams);
      next.set('page', p);
      return next;
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <div className="max-w-[1200px] mx-auto flex flex-col gap-5">
      <div className="flex items-center gap-2">
        <h1 className="text-xl font-bold text-gray-900 m-0">찜 목록</h1>
        {!loading && !error && (
          <span className="text-[13px] text-gray-500">총 {totalElements}개</span>
        )}
      </div>

      {error && <div className="error-box">{error}</div>}

      {!error && (
        <>
          {loading && items.length === 0 && (
            <div className="flex justify-center items-center py-24">
              <div className="w-7 h-7 rounded-full animate-spin border-[3px] border-gray-200 border-t-brand-600" />
            </div>
          )}

          {!loading && items.length === 0 && (
            <div className="flex flex-col items-center gap-3 py-24 text-gray-400">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
              </svg>
              <p className="text-[15px] m-0 font-medium">찜한 상품이 없습니다.</p>
              <Link to="/products" className="text-[13px] text-brand-600 underline no-underline">
                상품 둘러보기
              </Link>
            </div>
          )}

          {items.length > 0 && (
            <div
              className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-2 sm:gap-3 transition-opacity duration-150"
              style={{ opacity: loading ? 0.5 : 1 }}
            >
              {items.map((item) => (
                <WishlistCard key={item.productId} item={item} onRemove={handleRemove} />
              ))}
            </div>
          )}

          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-1 mt-4">
              <button
                onClick={() => handlePage(page - 1)}
                disabled={page === 0}
                className="w-8 h-8 flex items-center justify-center rounded-lg text-sm text-gray-500 disabled:opacity-30 hover:bg-gray-100 transition-colors bg-transparent border border-gray-200"
              >‹</button>
              {Array.from({ length: totalPages }, (_, i) => i).map((p) => (
                <button
                  key={p}
                  onClick={() => handlePage(p)}
                  className={`w-8 h-8 flex items-center justify-center rounded-lg text-sm font-medium transition-colors border ${
                    p === page
                      ? 'bg-brand-600 text-white border-brand-600'
                      : 'bg-transparent text-gray-600 border-gray-200 hover:bg-gray-100'
                  }`}
                >
                  {p + 1}
                </button>
              ))}
              <button
                onClick={() => handlePage(page + 1)}
                disabled={page >= totalPages - 1}
                className="w-8 h-8 flex items-center justify-center rounded-lg text-sm text-gray-500 disabled:opacity-30 hover:bg-gray-100 transition-colors bg-transparent border border-gray-200"
              >›</button>
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default WishlistPage;
