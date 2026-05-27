import { useState, useEffect, useCallback } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { getProducts, getCategories } from '../../api/product';

const formatPrice = (price) =>
  new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(price);

/* 상품 카드 */
const ProductCard = ({ product }) => (
  <Link to={`/products/${product.id}`} className="no-underline group">
    <div className="card p-0 overflow-hidden transition-shadow duration-200 hover:shadow-[0_4px_24px_rgba(0,0,0,0.12)] flex flex-col h-full">
      {/* 이미지 영역 */}
      <div className="w-full aspect-square bg-gray-100 flex items-center justify-center overflow-hidden">
        {product.imageUrl ? (
          <img
            src={product.imageUrl}
            alt={product.name}
            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
            loading="lazy"
          />
        ) : (
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#d1d5db" strokeWidth="1.5">
            <rect x="3" y="3" width="18" height="18" rx="2" />
            <circle cx="8.5" cy="8.5" r="1.5" />
            <polyline points="21 15 16 10 5 21" />
          </svg>
        )}
      </div>

      {/* 텍스트 영역 */}
      <div className="p-4 flex flex-col gap-1 flex-1">
        {product.categoryName && (
          <span className="text-[11px] font-semibold text-brand-600 uppercase tracking-wide">
            {product.categoryName}
          </span>
        )}
        <p className="text-sm font-semibold text-gray-900 leading-snug line-clamp-2 m-0">{product.name}</p>
        <p className="text-base font-bold text-gray-900 mt-auto m-0">{formatPrice(product.price)}</p>
        <p className={`text-[12px] m-0 ${product.stock > 0 ? 'text-green-500' : 'text-red-400'}`}>
          {product.stock > 0 ? `재고 ${product.stock}개` : '품절'}
        </p>
      </div>
    </div>
  </Link>
);

/* 페이지네이션 */
const Pagination = ({ page, totalPages, onChange }) => {
  if (totalPages <= 1) return null;
  const pages = Array.from({ length: totalPages }, (_, i) => i);

  return (
    <div className="flex items-center justify-center gap-1 mt-8">
      <button
        onClick={() => onChange(page - 1)}
        disabled={page === 0}
        className="w-8 h-8 flex items-center justify-center rounded-lg text-sm text-gray-500 disabled:opacity-30 hover:bg-gray-100 transition-colors"
      >‹</button>

      {pages.map((p) => (
        <button
          key={p}
          onClick={() => onChange(p)}
          className={`w-8 h-8 flex items-center justify-center rounded-lg text-sm font-medium transition-colors ${
            p === page
              ? 'bg-brand-600 text-white'
              : 'text-gray-600 hover:bg-gray-100'
          }`}
        >
          {p + 1}
        </button>
      ))}

      <button
        onClick={() => onChange(page + 1)}
        disabled={page >= totalPages - 1}
        className="w-8 h-8 flex items-center justify-center rounded-lg text-sm text-gray-500 disabled:opacity-30 hover:bg-gray-100 transition-colors"
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
    <div className="flex flex-col gap-6">
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
          className="h-11 px-5 text-white text-sm font-semibold rounded-[10px] border-none shrink-0"
          style={{ background: 'linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)' }}>
          검색
        </button>
      </form>

      {/* 카테고리 탭 */}
      {categories.length > 0 && (
        <div className="flex gap-2 flex-wrap">
          <button
            onClick={() => handleCategory('')}
            className={`h-8 px-3.5 text-[13px] font-medium rounded-full transition-colors ${
              !categoryId
                ? 'bg-brand-600 text-white'
                : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            }`}
          >
            전체
          </button>
          {categories.map((cat) => (
            <button
              key={cat.id}
              onClick={() => handleCategory(cat.id)}
              className={`h-8 px-3.5 text-[13px] font-medium rounded-full transition-colors ${
                String(categoryId) === String(cat.id)
                  ? 'bg-brand-600 text-white'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              {cat.name}
            </button>
          ))}
        </div>
      )}

      {/* 결과 수 */}
      {!loading && !error && (
        <p className="text-[13px] text-gray-500 m-0">
          {keyword && <><span className="font-semibold text-gray-800">"{keyword}"</span> 검색 결과 · </>}
          총 <span className="font-semibold text-gray-800">{totalElements}</span>개
        </p>
      )}

      {/* 로딩 */}
      {loading && (
        <div className="flex justify-center items-center py-20">
          <div className="w-7 h-7 rounded-full animate-spin border-[3px] border-gray-200 border-t-brand-600" />
        </div>
      )}

      {/* 에러 */}
      {error && (
        <div className="error-box">{error}</div>
      )}

      {/* 상품 그리드 */}
      {!loading && !error && (
        <>
          {products.length === 0 ? (
            <div className="flex flex-col items-center gap-3 py-20 text-gray-400">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <p className="text-[15px] m-0">상품이 없습니다.</p>
            </div>
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
              {products.map((p) => <ProductCard key={p.id} product={p} />)}
            </div>
          )}

          <Pagination page={page} totalPages={totalPages} onChange={handlePage} />
        </>
      )}
    </div>
  );
};

export default ProductListPage;
