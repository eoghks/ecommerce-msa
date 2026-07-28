import { useState, useEffect, useCallback, useRef, type FormEvent, type MouseEvent } from 'react';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import { getProducts, getCategories, getProductSuggestions } from '../../api/product';
import useCartStore from '../../store/cartStore';
import useAuthStore from '../../store/authStore';
import useWishlistStore from '../../store/wishlistStore';
import WishlistButton from '../../components/common/WishlistButton';
import type { Category, Product } from '../../types';

const formatPrice = (price: number) =>
  new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(price);

/* 정렬 옵션 — 서버 화이트리스트 키와 일치 */
const SORT_OPTIONS = [
  { value: 'latest', label: '최신순' },
  { value: 'price_asc', label: '가격 낮은순' },
  { value: 'price_desc', label: '가격 높은순' },
  { value: 'name', label: '이름순' },
];

const SUGGEST_DEBOUNCE_MS = 250;

/* 상품 카드 */
const ProductCard = ({ product }: { product: Product }) => {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuthStore();
  const addItem = useCartStore((s) => s.addItem);
  const [added, setAdded] = useState(false);
  const [imgLoaded, setImgLoaded] = useState(false);

  const handleAddCart = (e: MouseEvent<HTMLButtonElement>) => {
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

        {/* 이미지 영역 — aspect-square로 1:1 고정, 모든 자식 absolute로 레이아웃 영향 차단 */}
        <div className="relative w-full aspect-square overflow-hidden bg-gray-100">
          {/* 로딩 중 shimmer 스켈레톤 */}
          {!imgLoaded && product.imageUrl && (
            <div className="absolute inset-0 bg-gradient-to-r from-gray-100 via-gray-200 to-gray-100"
              style={{ backgroundSize: '200% 100%', animation: 'shimmer 1.4s infinite linear' }} />
          )}

          {product.imageUrl ? (
            <img
              src={product.imageUrl}
              alt={product.name}
              className="absolute inset-0 w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
              loading="lazy"
              onLoad={() => setImgLoaded(true)}
              style={{ opacity: imgLoaded ? 1 : 0, transition: 'opacity 0.35s ease' }}
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

          {/* 찜 하트 — 항상 표시 */}
          <WishlistButton productId={product.id} variant="card" />

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
        </div>{/* aspect-square 래퍼 닫기 */}

        {/* 텍스트 영역 */}
        <div className="p-2.5 flex flex-col gap-0.5 flex-1">
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

interface PaginationProps {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}

/* 페이지네이션 */
const Pagination = ({ page, totalPages, onChange }: PaginationProps) => {
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
  const { isAuthenticated } = useAuthStore();
  const fetchWishlistIds = useWishlistStore((s) => s.fetchIds);

  const [categories, setCategories] = useState<Category[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [keyword, setKeyword] = useState(searchParams.get('keyword') || '');
  const [inputKeyword, setInputKeyword] = useState(searchParams.get('keyword') || '');
  const categoryId = searchParams.get('categoryId') || '';
  const page = Number(searchParams.get('page') || 0);
  const size = Number(searchParams.get('size') || 10);
  const sort = searchParams.get('sort') || 'latest';
  const minPrice = searchParams.get('minPrice') || '';
  const maxPrice = searchParams.get('maxPrice') || '';

  // 가격대 입력 (로컬 상태 — 적용 버튼으로 URL 반영)
  const [minPriceInput, setMinPriceInput] = useState(minPrice);
  const [maxPriceInput, setMaxPriceInput] = useState(maxPrice);
  const [priceError, setPriceError] = useState('');

  // 자동완성 상태
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const suggestTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  /* 카테고리 목록 로드 */
  useEffect(() => {
    getCategories()
      .then((res) => setCategories(res.data))
      .catch(() => {});
  }, []);

  /* 로그인 상태면 찜 ID 집합 로드 (하트 표시용) */
  useEffect(() => {
    if (isAuthenticated) fetchWishlistIds();
  }, [isAuthenticated, fetchWishlistIds]);

  /* 상품 목록 로드 */
  const fetchProducts = useCallback(() => {
    setLoading(true);
    setError('');
    const params: Record<string, unknown> = { page, size, sort };
    if (keyword) params.keyword = keyword;
    if (categoryId) params.categoryId = categoryId;
    if (minPrice) params.minPrice = minPrice;
    if (maxPrice) params.maxPrice = maxPrice;

    getProducts(params)
      .then((res) => {
        setProducts(res.data.content ?? []);
        setTotalPages(res.data.totalPages ?? 0);
        setTotalElements(res.data.totalElements ?? 0);
      })
      .catch(() => setError('상품을 불러오는 데 실패했습니다.'))
      .finally(() => setLoading(false));
  }, [page, size, keyword, categoryId, sort, minPrice, maxPrice]);

  useEffect(() => { fetchProducts(); }, [fetchProducts]);

  /* 자동완성 — 입력 debounce 후 후보 조회 */
  useEffect(() => {
    if (suggestTimer.current) clearTimeout(suggestTimer.current);
    const trimmed = inputKeyword.trim();
    if (!trimmed) {
      setSuggestions([]);
      return;
    }
    suggestTimer.current = setTimeout(() => {
      getProductSuggestions(trimmed)
        .then((res) => setSuggestions(res.data ?? []))
        .catch(() => setSuggestions([]));
    }, SUGGEST_DEBOUNCE_MS);
    return () => { if (suggestTimer.current) clearTimeout(suggestTimer.current); };
  }, [inputKeyword]);

  /* 키워드로 검색 실행 (검색바·자동완성 공통) */
  const runSearch = (value: string) => {
    setKeyword(value);
    setShowSuggestions(false);
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (value) next.set('keyword', value);
      else next.delete('keyword');
      next.delete('page');
      return next;
    });
  };

  const handleSearch = (e: FormEvent) => {
    e.preventDefault();
    runSearch(inputKeyword);
  };

  /* 자동완성 후보 선택 → 즉시 검색 */
  const handleSelectSuggestion = (name: string) => {
    setInputKeyword(name);
    runSearch(name);
  };

  /* 정렬 변경 */
  const handleSort = (value: string) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set('sort', value);
      next.delete('page');
      return next;
    });
  };

  /* 가격대 적용 — min ≤ max, 음수 불가 검증 */
  const handleApplyPrice = () => {
    const min = minPriceInput.trim();
    const max = maxPriceInput.trim();
    if ((min && Number(min) < 0) || (max && Number(max) < 0)) {
      setPriceError('가격은 0 이상이어야 합니다.');
      return;
    }
    if (min && max && Number(min) > Number(max)) {
      setPriceError('최소 가격이 최대 가격보다 클 수 없습니다.');
      return;
    }
    setPriceError('');
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (min) next.set('minPrice', min); else next.delete('minPrice');
      if (max) next.set('maxPrice', max); else next.delete('maxPrice');
      next.delete('page');
      return next;
    });
  };

  const handleCategory = (id: string | number) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (id) next.set('categoryId', String(id));
      else next.delete('categoryId');
      next.delete('page');
      return next;
    });
  };

  const handlePage = (p: number) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set('page', String(p));
      return next;
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleSize = (s: number) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set('size', String(s));
      next.delete('page'); // 사이즈 변경 시 1페이지로 초기화
      return next;
    });
  };

  return (
    <div className="flex flex-col gap-5">
      {/* 검색창 */}
      <form onSubmit={handleSearch} className="flex gap-2">
        <div className="input-wrapper flex-1 relative">
          <span className="input-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#9ca3af" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
            </svg>
          </span>
          <input
            type="text"
            value={inputKeyword}
            onChange={(e) => { setInputKeyword(e.target.value); setShowSuggestions(true); }}
            onFocus={() => setShowSuggestions(true)}
            onBlur={() => setTimeout(() => setShowSuggestions(false), 150)}
            placeholder="상품명으로 검색"
            className="input-field"
            autoComplete="off"
          />

          {/* 자동완성 후보 드롭다운 */}
          {showSuggestions && suggestions.length > 0 && (
            <ul className="absolute left-0 right-0 top-full mt-1 z-20 bg-white rounded-xl overflow-hidden list-none m-0 p-0"
              style={{ border: '1px solid #e5e7eb', boxShadow: '0 8px 24px rgba(0,0,0,0.10)' }}>
              {suggestions.map((name) => (
                <li key={name}>
                  <button
                    type="button"
                    onMouseDown={(e) => e.preventDefault()}
                    onClick={() => handleSelectSuggestion(name)}
                    className="w-full text-left px-4 py-2.5 text-[13px] text-gray-700 bg-transparent border-none hover:bg-gray-50 transition-colors cursor-pointer"
                  >
                    {name}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
        <button type="submit"
          className="h-11 px-5 text-white text-sm font-semibold rounded-[10px] border-none shrink-0 transition-opacity hover:opacity-90"
          style={{ background: 'linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)' }}>
          검색
        </button>
      </form>

      {/* 정렬 + 가격대 필터 */}
      <div className="flex flex-wrap items-end gap-3">
        <div className="flex flex-col gap-1">
          <span className="text-[12px] text-gray-400">정렬</span>
          <select
            value={sort}
            onChange={(e) => handleSort(e.target.value)}
            className="h-9 px-3 text-[13px] text-gray-700 rounded-lg bg-white border border-gray-200 focus:border-brand-600 outline-none"
          >
            {SORT_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-1">
          <span className="text-[12px] text-gray-400">가격대 (원)</span>
          <div className="flex items-center gap-1.5">
            <input
              type="number"
              min="0"
              value={minPriceInput}
              onChange={(e) => setMinPriceInput(e.target.value)}
              placeholder="최소"
              className="h-9 w-24 px-2.5 text-[13px] rounded-lg bg-white border border-gray-200 focus:border-brand-600 outline-none"
            />
            <span className="text-gray-400 text-sm">~</span>
            <input
              type="number"
              min="0"
              value={maxPriceInput}
              onChange={(e) => setMaxPriceInput(e.target.value)}
              placeholder="최대"
              className="h-9 w-24 px-2.5 text-[13px] rounded-lg bg-white border border-gray-200 focus:border-brand-600 outline-none"
            />
            <button
              type="button"
              onClick={handleApplyPrice}
              className="h-9 px-4 text-[13px] font-medium text-gray-700 rounded-lg bg-white border border-gray-200 hover:border-brand-600 hover:text-brand-600 transition-colors"
            >
              적용
            </button>
          </div>
        </div>

        {priceError && <span className="text-[12px] text-red-500 pb-2">{priceError}</span>}
      </div>

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

      {/* 결과 수 + 페이지당 개수 선택 */}
      {!loading && !error && (
        <div className="flex items-center justify-between">
          <p className="text-[13px] text-gray-500 m-0">
            {keyword && <><span className="font-semibold text-gray-800">"{keyword}"</span> 검색결과 · </>}
            총 <span className="font-semibold text-gray-800">{totalElements}</span>개 상품
          </p>
          <div className="flex items-center gap-1">
            <span className="text-[12px] text-gray-400 mr-1">페이지당</span>
            {[10, 20].map((s) => (
              <button
                key={s}
                onClick={() => handleSize(s)}
                className={`h-7 px-3 text-[12px] font-medium rounded-md border transition-all duration-150 ${
                  size === s
                    ? 'bg-brand-600 text-white border-brand-600'
                    : 'bg-white text-gray-600 border-gray-200 hover:border-brand-600 hover:text-brand-600'
                }`}
              >
                {s}개
              </button>
            ))}
          </div>
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
                  onClick={() => {
                    setInputKeyword(''); setKeyword('');
                    setMinPriceInput(''); setMaxPriceInput(''); setPriceError('');
                    setSearchParams({});
                  }}
                  className="text-[13px] text-brand-600 underline bg-transparent border-none"
                >
                  전체 상품 보기
                </button>
              )}
            </div>
          )}

          {products.length > 0 && (
            <div
              className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-2 sm:gap-3 transition-opacity duration-150"
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
