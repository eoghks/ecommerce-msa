import { useState, useEffect, useRef, type ChangeEvent, type FormEvent } from 'react';
import type { AxiosError } from 'axios';
import { getCategories, getProducts, getMyProducts, createProduct, updateProduct, deleteProduct, uploadProductImage, banProduct, unbanProduct } from '../../api/product';
import useAuthStore from '../../store/authStore';
import type { ApiErrorResponse, Category, Product, ProductPayload } from '../../types';

interface ProductFormState {
  name: string;
  description: string;
  price: string | number;
  stock: string | number;
  imageUrl: string;
  categoryId: string | number;
}

const EMPTY_FORM: ProductFormState = { name: '', description: '', price: '', stock: '', imageUrl: '', categoryId: '' };

const AdminProductPage = () => {
  const { role } = useAuthStore();
  const isAdmin = role === 'ADMIN';

  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Product | null>(null); // null = 신규, product = 수정
  const [form, setForm] = useState<ProductFormState>(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [deleteId, setDeleteId] = useState<number | null>(null);
  const [imgUploading, setImgUploading] = useState(false);
  const [imgPreview, setImgPreview] = useState('');
  const fileRef = useRef<HTMLInputElement>(null);

  const load = async () => {
    setLoading(true);
    try {
      const [catRes, prodRes] = await Promise.all([
        getCategories(),
        isAdmin ? getProducts({ size: 100, includeBanned: true }) : getMyProducts({ size: 100 }),
      ]);
      setCategories(catRes.data ?? []);
      setProducts(prodRes.data.content ?? []);
    } catch {
      setError('상품 목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleImageFile = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setImgPreview(URL.createObjectURL(file));
    setImgUploading(true);
    try {
      const res = await uploadProductImage(file);
      setForm((p) => ({ ...p, imageUrl: res.data.url }));
    } catch {
      setError('이미지 업로드에 실패했습니다.');
    } finally {
      setImgUploading(false);
    }
  };

  const openNew = () => {
    setEditing(null);
    setForm(EMPTY_FORM);
    setImgPreview('');
    setError('');
    setFormOpen(true);
  };

  const openEdit = (p: Product) => {
    setEditing(p);
    setImgPreview(p.imageUrl ?? '');
    setForm({
      name: p.name, description: p.description ?? '',
      price: String(p.price), stock: String(p.stock),
      imageUrl: p.imageUrl ?? '', categoryId: String(p.categoryId),
    });
    setError('');
    setFormOpen(true);
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    if (!form.name || !form.price || form.stock === '' || !form.categoryId) {
      setError('상품명, 가격, 재고, 카테고리는 필수입니다.'); return;
    }
    if (Number(form.price) < 1) {
      setError('가격은 1원 이상이어야 합니다.'); return;
    }
    if (Number(form.stock) < 0) {
      setError('재고는 0 이상이어야 합니다.'); return;
    }
    setSubmitting(true);
    const payload: ProductPayload = {
      name: form.name, description: form.description,
      price: Number(form.price), stock: Number(form.stock),
      imageUrl: form.imageUrl || null, categoryId: Number(form.categoryId),
    };
    try {
      if (editing) {
        await updateProduct(editing.id, payload);
      } else {
        await createProduct(payload);
      }
      setFormOpen(false);
      await load();
    } catch (err) {
      setError((err as AxiosError<ApiErrorResponse>).response?.data?.detail || '저장에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteProduct(id);
      setDeleteId(null);
      await load();
    } catch (err) {
      setError((err as AxiosError<ApiErrorResponse>).response?.data?.detail || '삭제에 실패했습니다.');
      setDeleteId(null);
    }
  };

  const handleBanToggle = async (product: Product) => {
    try {
      if (product.status === 'BANNED') {
        await unbanProduct(product.id);
      } else {
        await banProduct(product.id);
      }
      await load();
    } catch (err) {
      setError((err as AxiosError<ApiErrorResponse>).response?.data?.detail || '판매 상태 변경에 실패했습니다.');
    }
  };

  const formatPrice = (p: number) =>
    new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(p);

  return (
    <div className="max-w-[960px] mx-auto">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-bold text-gray-900 m-0">
          {isAdmin ? '상품 관리 (전체)' : '내 상품 관리'}
        </h1>
        <button onClick={openNew} className="btn-brand-fill text-sm">+ 상품 등록</button>
      </div>

      {error && <div className="error-box mb-4">{error}</div>}

      {/* 상품 등록/수정 폼 */}
      {formOpen && (
        <div className="bg-white border border-gray-100 rounded-2xl p-5 mb-6 shadow-sm">
          <h2 className="text-[15px] font-bold text-gray-800 mb-1 m-0">
            {editing ? '상품 수정' : '새 상품 등록'}
          </h2>
          {/* 관리자: 수정 중인 상품의 판매자 정보 */}
          {isAdmin && editing && (
            <div className="text-[12px] text-gray-500 mb-4">
              판매자:{' '}
              {editing.sellerId
                ? <span className="text-emerald-600 font-medium">
                    {editing.sellerName || `#${editing.sellerId}`}
                    {editing.sellerEmail ? ` (${editing.sellerEmail})` : ''}
                  </span>
                : <span className="text-amber-600 font-medium">플랫폼(관리자) 등록 상품</span>}
            </div>
          )}
          <form onSubmit={handleSubmit} className="flex flex-col gap-3">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="flex flex-col gap-1.5">
                <label className="field-label">상품명 *</label>
                <input className="input-field" value={form.name}
                  onChange={(e) => setForm(p => ({ ...p, name: e.target.value }))}
                  placeholder="상품명 입력" />
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="field-label">카테고리 *</label>
                <select className="input-field" value={form.categoryId}
                  onChange={(e) => setForm(p => ({ ...p, categoryId: e.target.value }))}>
                  <option value="">카테고리 선택</option>
                  {categories.map(c => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="field-label">가격 (원) *</label>
                <input className="input-field" type="number" value={form.price}
                  onChange={(e) => setForm(p => ({ ...p, price: Math.max(0, Number(e.target.value)) }))}
                  placeholder="0" min="1" />
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="field-label">재고 *</label>
                <input className="input-field" type="number" value={form.stock}
                  onChange={(e) => setForm(p => ({ ...p, stock: Math.max(0, Number(e.target.value)) }))}
                  placeholder="0" min="0" />
              </div>
            </div>
            {/* 이미지 업로드 */}
            <div className="flex flex-col gap-1.5">
              <label className="field-label">상품 이미지</label>
              <div className="flex items-center gap-3">
                {/* 미리보기 */}
                <div className="w-16 h-16 shrink-0 rounded-xl bg-gray-100 overflow-hidden flex items-center justify-center">
                  {imgUploading ? (
                    <div className="w-5 h-5 rounded-full animate-spin border-2 border-gray-300 border-t-brand-600" />
                  ) : imgPreview ? (
                    <img src={imgPreview} alt="preview" className="w-full h-full object-cover" />
                  ) : (
                    <span className="text-gray-300 text-2xl">📷</span>
                  )}
                </div>
                <div className="flex flex-col gap-1.5 flex-1">
                  <button type="button"
                    onClick={() => fileRef.current?.click()}
                    disabled={imgUploading}
                    className="h-9 px-4 text-[13px] font-medium text-brand-600 border border-brand-200 rounded-lg hover:bg-brand-50 bg-white transition-colors w-fit">
                    {imgUploading ? '업로드 중...' : '파일 선택'}
                  </button>
                  <span className="text-[11px] text-gray-400">JPG, PNG, WebP · 최대 10MB</span>
                  <input ref={fileRef} type="file" accept="image/*"
                    className="hidden" onChange={handleImageFile} />
                </div>
              </div>
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="field-label">상품 설명</label>
              <textarea className="input-field" rows={3} value={form.description}
                onChange={(e) => setForm(p => ({ ...p, description: e.target.value }))}
                placeholder="상품 설명 입력" style={{ resize: 'vertical' }} />
            </div>
            {error && <div className="error-box">{error}</div>}
            <div className="flex justify-end gap-2 mt-1">
              <button type="button" onClick={() => setFormOpen(false)}
                className="h-10 px-4 text-sm font-medium text-gray-600 bg-white border border-gray-200 rounded-[10px]">
                취소
              </button>
              <button type="submit" disabled={submitting}
                className="h-10 px-5 text-white text-sm font-semibold rounded-[10px] border-none disabled:opacity-70"
                style={{ background: 'linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)' }}>
                {submitting ? '저장 중...' : (editing ? '수정 완료' : '등록')}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* 상품 목록 */}
      {loading ? (
        <div className="flex justify-center items-center py-24">
          <div className="w-7 h-7 rounded-full animate-spin border-[3px] border-gray-200 border-t-brand-600" />
        </div>
      ) : products.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-24 text-center">
          <p className="text-gray-400 text-[15px] m-0">등록된 상품이 없습니다.</p>
          <button onClick={openNew} className="btn-brand-fill text-sm">첫 상품 등록하기</button>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {products.map((p) => (
            <div key={p.id}
              className="bg-white border border-gray-100 rounded-2xl p-4 flex items-center gap-4">
              {/* 이미지 */}
              <div className="w-14 h-14 shrink-0 rounded-xl bg-gray-100 overflow-hidden">
                {p.imageUrl
                  ? <img src={p.imageUrl} alt={p.name} className="w-full h-full object-cover" />
                  : <div className="w-full h-full flex items-center justify-center text-gray-300 text-xl">📦</div>
                }
              </div>
              {/* 정보 */}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-[14px] font-semibold text-gray-900 truncate">{p.name}</span>
                  <span className="text-[11px] px-2 py-0.5 rounded-full bg-gray-100 text-gray-500 shrink-0">
                    {p.categoryName}
                  </span>
                  {p.status === 'BANNED' && (
                    <span className="text-[11px] px-2 py-0.5 rounded-full bg-red-100 text-red-600 font-semibold shrink-0">
                      판매 금지됨
                    </span>
                  )}
                </div>
                <div className="flex items-center gap-3 mt-1">
                  <span className="text-[13px] font-bold text-brand-600">{formatPrice(p.price)}</span>
                  <span className="text-[12px] text-gray-400">재고 {p.stock}개</span>
                  {isAdmin && (p.sellerId
                    ? (
                      <span className="text-[11px] text-emerald-600 bg-emerald-50 px-2 py-0.5 rounded-full">
                        {p.sellerName ? `${p.sellerName}` : `판매자 #${p.sellerId}`}
                        {p.sellerEmail ? ` · ${p.sellerEmail}` : ''}
                      </span>
                    )
                    : (
                      <span className="text-[11px] text-amber-600 bg-amber-50 px-2 py-0.5 rounded-full">
                        플랫폼(관리자)
                      </span>
                    )
                  )}
                </div>
              </div>
              {/* 버튼 — ADMIN: 판매 금지/해제 (운영 검열) / SELLER: 수정·삭제 (본인 상품) */}
              <div className="flex items-center gap-2 shrink-0">
                {isAdmin ? (
                  p.status === 'BANNED' ? (
                    <button onClick={() => handleBanToggle(p)}
                      className="h-8 px-3 text-[12px] font-medium text-emerald-600 border border-emerald-200 rounded-lg hover:bg-emerald-50 bg-white transition-colors">
                      판매 재개
                    </button>
                  ) : (
                    <button onClick={() => handleBanToggle(p)}
                      className="h-8 px-3 text-[12px] font-medium text-red-500 border border-red-200 rounded-lg hover:bg-red-50 bg-white transition-colors">
                      판매 금지
                    </button>
                  )
                ) : (
                  <>
                    <button onClick={() => openEdit(p)}
                      className="h-8 px-3 text-[12px] font-medium text-brand-600 border border-brand-200 rounded-lg hover:bg-brand-50 bg-white transition-colors">
                      수정
                    </button>
                    {deleteId === p.id ? (
                      <div className="flex items-center gap-1">
                        <span className="text-[12px] text-red-500 font-medium">삭제?</span>
                        <button onClick={() => handleDelete(p.id)}
                          className="h-8 px-2 text-[12px] text-white bg-red-500 rounded-lg border-none">확인</button>
                        <button onClick={() => setDeleteId(null)}
                          className="h-8 px-2 text-[12px] text-gray-500 bg-gray-100 rounded-lg border-none">취소</button>
                      </div>
                    ) : (
                      <button onClick={() => setDeleteId(p.id)}
                        className="h-8 px-3 text-[12px] font-medium text-red-500 border border-red-200 rounded-lg hover:bg-red-50 bg-white transition-colors">
                        삭제
                      </button>
                    )}
                  </>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default AdminProductPage;
