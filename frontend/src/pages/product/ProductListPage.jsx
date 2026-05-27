import { useState, useEffect, useCallback } from 'react';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import { getProducts, getCategories } from '../../api/product';
import useCartStore from '../../store/cartStore';
import useAuthStore from '../../store/authStore';

const formatPrice = (price) =>
  new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(price);

/* 상품 카드 */
const ProductCard = ({ product }) => {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuthStore();
  const addItem = useCartStore((s) => s.addItem);
  const [added, setAdded] = useState(false);

  const handleAddCart = (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (!isAuthenticated) {
      navigate('/login', { state: { from: `/products/${product.id}` } });
      return;
    }
    addItem(product, 1);
    setAdded(true);
    setTimeout(() => setAdded(false), 1500);
  };

  return (
    <Link to={`/products/${product.id}`} className="no-underline group block">
      <div className="bg-white rounded-2xl overflow-hidden flex flex-col h-full transition-all duration-200 hover:shadow-[0_8px_32px_rgba(0,0,0,0.12)] hover:-translate-y-0.5"
        style={{ border: '1px solid #e5e7eb', boxShadow: '0 2px 8px rgba(0,0,0,0.06)' }}>

        {/* 이미지 영역 */}
        <div className="relative w-full aspect-square bg-gray-50 overflow-hidden">
          {product.imageUrl ? (
            <img
              src={product.imageUrl}
              alt={product.name}
              className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
              loading="lazy"
            />
          ) : (
            <div className="w-full h-full flex items-center justify-center bg-gradient-to-br from-gray-100 to-gray-200">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#9ca3af" strokeWidth="1.5">
                <rect x="3" y="3" width="18" height="18" rx="2" />
                <circle cx="8.5" cy="8.5" r="1.5" />
                <polyline points="21 15 16 10 5 21" />
              </svg>
            </div>
          )}

          {/* 품절 배지 */}
          {product.stock === 0 && (
            <div className="absolute inset-0 bg-black/40 flex items-center justify-center">
              <span className="bg-white/90 text-gray-700 text-xs font-bold px-3 py-1 rounded-full">품절</span>
            </div>
          )}

          {/* 장바구니 버튼 — hover 시 표시 */}
          {product.stock > 0 && (
            <button
              onClick={handleAddCart}
              className="absolute bottom-3 right-3 w-9 h-9 rounded-full flex items-center justify-center text-white opacity-0 group-hover:opacity-100 transition-all duration-200 border-none shadow-lg"
              style={{
                background: added
                  ? 'linear-gradient(135deg, #22c55e 0%, #16a34a 100%)'
                  : 'linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)',
                transform: 'translateY(4px)',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.transform = 'translateY(0)')}
              onMouseLeave={(e) => (e.currentTarget.style.transform = 'translateY(4px)')}
              title="장바구니 담기"
            >
              {added ? (
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
              ) : (
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/>
                  <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"/>
                </svg>
              )}
            </button>
          )}
        </div>

        {/* 텍스트 영역 */}
        <div className="p-3.5 flex flex-col gap-1 flex-1">
          {product.categoryName && (
            <span className="text-[11px] font-semibold text-brand-600 uppercase tracking-wide">
              {product.categoryName}
            </span>
          )}
          <p className="text-[13px] font-semibold text-gray-900 leading-snug line-clamp-2 m-0 flex-1">
            {product.name}
          </p>
          <p className="text-[15px] font-bold text-gray-900 m-0 mt-1">{formatPrice(product.price)}</p>
          {product.stock > 0 && product.stock <= 10 && (
            <p className="text-[11px] text-orange-500 m-0 font-medium">잔여 {product.stock}개</p>
          )}
        </div>
      </div>
    </Link>
  );
};

/* 페이지네이션 */
const Pagination = ({ page, totalPages, onChange }) => {
  if (totalPages <= 1) return null;

  // 최대 7페이지 표시
  const maxShow = 7;
  let start = Math.max(0, page - Math.floor(maxShow / 2));
  const end = Math.min(totalPages, start + maxShow);
  if (end - start < maxShow) start = Math.max(0, end - maxShow);
  const pages = Array.from({ length: end - start }, (_, i) => i + start);

  return (
    <div className="flex items-center justify-center gap-1 mt-8">
      <button
        onClick={() => onChange(page - 1)}
        disabled={page === 0}
        className="w-8 h-8 flex items-center justify-center rounded-lg text-sm text-gray-500 disabled:opacity-30 hover:bg-gray-100 transition-colors bg-transparent border border-gray-200"
      >‹</button>

      {start > 0 && (
        <>
          <button onClick={() => onChange(0)} className="w-8 h-8 flex items-center justify-center rounded-lg text-sm text-gray-600 hover:bg-gray-100 transition-colors bg-transparent border border-gray-200">1</button>
          <span className="w-8 text-center text-gray-400 text-sm">…</span>
        </>
      )}

      {pages.map((p) => (
        <button
          key={p}
          onClick={() => onChange(p)}
          className={`w-8 h-8 flex items-center justify-center rounded-lg text-sm font-medium transition-colors border ${
            p === page
              ? 'bg-brand-600 text-white border-brand-600'
              : 'bg-transparent text-gray-600 border-gray-200 hover:bg-gray-100'
          }`}
        >
          {p + 1}
        </button>
      ))}

      {end < totalPages && (
        <>
          <span className="w-8 text-center text-gray-400 text-sm">…</span>
          <button onClick={() => onChange(totalPages - 1)} className="w-8 h-8 flex items-center justify-center rounded-lg text-sm text-gray-600 hover:bg-gray-100 transition-colors bg-transparent border border-gray-200">{totalPages}</button>
        </>
      )}

      <button
        onClick={() => onChange(page + 1)}
        disabled={page >= totalPages - 1}
        className="w-8 h-8 flex items-center justify-center rounded-lg text-sm text-gray-500 disabled:opacity-30 hover:bg-gray-100 transition-colors bg-transparent border border-gray-200"
      >›</button>
    </div>
  );
};

/* 메인 페이지 */
const ProductListPage = () => {
  const [searchParams, setSearchParams] = useSearchParams();

  const [categories, setCategories] = useState([]);
  const [products, setProducts] = useState([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [keyword, setKeyword] = useState(searchParams.get('keyword') || '');
  const [inputKeyword, setInputKeyword] = useState(searchParams.get('keyword') || '');
  const categoryId = searchParams.get('categoryId') || '';
  const page = Number(searchParams.get('page') || 0);

  /* 카테고리 목록 로드 */
  useEffect(() => {
    getCategories()
      .then((res) => setCategories(res.data))
      .catch(() => {});
  }, []);

  /* 상품 목록 로드 */
  const fetchProducts = useCallback(() => {
    setLoading(true);
    setError('');
    const params = { page, size: 12, sort: 'createdAt,desc' };
    if (keyword) params.keyword = keyword;
    if (categoryId) params.categoryId = categoryId;

    getProducts(params)
      .then((res) => {
        setProducts(res.data.content ?? []);
        setTotalPages(res.data.totalPages ?? 0);
        setTotalElements(res.data.totalElements ?? 0);
      })
      .catch(() => setError('상품을 불러오는 데 실패했습니다.'))
      .finally(() => setLoading(false));
  }, [page, keyword, categoryId]);

  useEffect(() => { fetchProducts(); }, [fetchProducts]);

  const handleSearch = (e) => {
    e.preventDefault();
    setKeyword(inputKeyword);
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (inputKeyword) next.set('keyword', inputKeyword);
      else next.delete('keyword');
      next.delete('page');
      return next;
    });
  };

  const handleCategory = (id) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (id) next.set('categoryId', id);
      else next.delete('categoryId');
      next.delete('page');
      return next;
    });
  };

  const handlePage = (p) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set('page', p);
      return next;
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <div className="flex flex-col gap-5">
      {/* 검색창 */}
      <form onSubmit={handleSearch} className="flex gap-2">
        <div className="input-wrapper flex-1">
          <span className="input-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#9ca3af" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
            </svg>
          </span>
          <input
            type="text"
            value={inputKeyword}
            onChange={(e) => setInputKeyword(e.target.value)}
            placeholder="상품명으로 검색"
            className="input-field"
          />
        </div>
        <button type="submit"
          className="h-11 px-5 text-white text-sm font-semibold rounded-[10px] border-none shrink-0 transition-opacity hover:opacity-90"
          style={{ background: 'linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)' }}>
          검색
        </button>
      </form>

      {/* 카테고리 탭 */}
      {categories.length > 0 && (
        <div className="flex gap-2 flex-wrap">
          {[{ id: '', name: '전체' }, ...categories].map((cat) => (
            <button
              key={cat.id}
              onClick={() => handleCategory(cat.id)}
              className={`h-8 px-4 text-[13px] font-medium rounded-full transition-all duration-150 border ${
                String(categoryId) === String(cat.id)
                  ? 'bg-brand-600 text-white border-brand-600 shadow-sm'
                  : 'bg-white text-gray-600 border-gray-200 hover:border-brand-600 hover:text-brand-600'
              }`}
            >
              {cat.name}
            </button>
          ))}
        </div>
      )}

      {/* 결과 수 */}
      {!loading && !error && (
        <div className="flex items-center justify-between">
          <p className="text-[13px] text-gray-500 m-0">
            {keyword && <><span className="font-semibold text-gray-800">"{keyword}"</span> 검색결과 · </>}
            총 <span className="font-semibold text-gray-800">{totalElements}</span>개 상품
          </p>
        </div>
      )}

      {/* 에러 */}
      {error && <div className="error-box">{error}</div>}

      {/* 상품 그리드 — 로딩 중에도 이전 데이터 유지 (레이아웃 시프트 방지) */}
      {!error && (
        <>
          {/* 첫 로딩 스피너 (products가 아직 없을 때만) */}
          {loading && products.length === 0 && (
            <div className="flex justify-center items-center py-24">
              <div className="w-7 h-7 rounded-full animate-spin border-[3px] border-gray-200 border-t-brand-600" />
            </div>
          )}

          {!loading && products.length === 0 && (
            <div className="flex flex-col items-center gap-3 py-24 text-gray-400">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <p className="text-[15px] m-0 font-medium">검색 결과가 없습니다.</p>
              {(keyword || categoryId) && (
                <button
                  onClick={() => { setInputKeyword(''); setKeyword(''); setSearchParams({}); }}
                  className="text-[13px] text-brand-600 underline bg-transparent border-none"
                >
                  전체 상품 보기
                </button>
              )}
            </div>
          )}

          {products.length > 0 && (
            <div
              className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3 sm:gap-4 transition-opacity duration-150"
              style={{ opacity: loading ? 0.5 : 1 }}
            >
              {products.map((p) => <ProductCard key={p.id} product={p} />)}
            </div>
          )}

          {!loading && <Pagination page={page} totalPages={totalPages} onChange={handlePage} />}
        </>
      )}
    </div>
  );
};

export default ProductListPage;
