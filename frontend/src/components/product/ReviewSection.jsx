import { useState, useEffect, useCallback } from 'react';
import { getReviews, createReview, updateReview, deleteReview } from '../../api/product';
import useAuthStore from '../../store/authStore';
import StarRating from '../common/StarRating';

const PAGE_SIZE = 10;
const MAX_CONTENT = 1000;

// 서버 실패 메시지 추출 — ProblemDetail(detail) 우선
const resolveError = (err, fallback) => err?.response?.data?.detail || fallback;

const formatDate = (v) =>
  v ? new Date(v).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' }) : '';

/**
 * 상품 상세의 리뷰 탭 (V1.1-1).
 * 목록(페이징) + 작성/수정/삭제 폼. 구매 인증 실패(403)·중복(409) 피드백을 노출한다.
 */
const ReviewSection = ({ productId, onChanged }) => {
  const { isAuthenticated, userId, role } = useAuthStore();
  const [reviews, setReviews] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);

  const load = useCallback((p) => {
    setLoading(true);
    getReviews(productId, p, PAGE_SIZE)
      .then((res) => {
        setReviews(res.data.content ?? []);
        setTotalPages(res.data.totalPages ?? 0);
        setPage(res.data.number ?? p);
      })
      .catch(() => setReviews([]))
      .finally(() => setLoading(false));
  }, [productId]);

  useEffect(() => { load(0); }, [load]);

  // 로그인 사용자가 이미 작성한 리뷰 (있으면 수정/삭제 모드)
  const myReview = reviews.find((r) => String(r.userId) === String(userId));

  const refresh = () => {
    load(page);
    onChanged?.();
  };

  return (
    <div className="mt-10">
      <h2 className="text-lg font-bold text-gray-900 m-0 mb-4">상품 리뷰</h2>

      {isAuthenticated && (
        <ReviewForm productId={productId} existing={myReview} onSaved={refresh} />
      )}
      {!isAuthenticated && (
        <p className="text-[13px] text-gray-400 mb-4">리뷰 작성은 로그인 후 이용할 수 있습니다.</p>
      )}

      {loading ? (
        <div className="flex justify-center py-10">
          <div className="w-6 h-6 rounded-full animate-spin border-[3px] border-gray-200 border-t-brand-600" />
        </div>
      ) : reviews.length === 0 ? (
        <p className="text-[14px] text-gray-400 py-6 text-center m-0">아직 등록된 리뷰가 없습니다.</p>
      ) : (
        <div className="flex flex-col gap-3">
          {reviews.map((r) => (
            <ReviewItem
              key={r.reviewId}
              review={r}
              canDelete={String(r.userId) === String(userId) || role === 'ADMIN'}
              onDeleted={refresh}
              productId={productId}
            />
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex justify-center items-center gap-3 mt-5">
          <button disabled={page <= 0} onClick={() => load(page - 1)}
            className="h-8 px-3 text-[13px] rounded-lg border border-gray-200 bg-white disabled:opacity-40">
            이전
          </button>
          <span className="text-[13px] text-gray-500">{page + 1} / {totalPages}</span>
          <button disabled={page >= totalPages - 1} onClick={() => load(page + 1)}
            className="h-8 px-3 text-[13px] rounded-lg border border-gray-200 bg-white disabled:opacity-40">
            다음
          </button>
        </div>
      )}
    </div>
  );
};

const ReviewForm = ({ productId, existing, onSaved }) => {
  const [rating, setRating] = useState(existing?.rating ?? 5);
  const [content, setContent] = useState(existing?.content ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const editing = Boolean(existing);

  useEffect(() => {
    setRating(existing?.rating ?? 5);
    setContent(existing?.content ?? '');
  }, [existing]);

  const submit = () => {
    setError('');
    if (rating < 1 || rating > 5) { setError('별점을 선택하세요.'); return; }
    setSaving(true);
    const body = { rating, content };
    const req = editing
      ? updateReview(productId, existing.reviewId, body)
      : createReview(productId, body);
    req
      .then(() => { onSaved?.(); if (!editing) setContent(''); })
      .catch((err) => setError(resolveError(err, '리뷰 저장에 실패했습니다.')))
      .finally(() => setSaving(false));
  };

  return (
    <div className="bg-gray-50 border border-gray-100 rounded-xl p-4 mb-5">
      <div className="flex items-center gap-2 mb-2">
        <span className="text-[13px] font-medium text-gray-700">{editing ? '내 리뷰 수정' : '리뷰 작성'}</span>
        <StarRating value={rating} onChange={setRating} />
      </div>
      <textarea
        value={content}
        maxLength={MAX_CONTENT}
        onChange={(e) => setContent(e.target.value)}
        placeholder="상품에 대한 후기를 남겨주세요."
        className="w-full h-20 text-[13px] p-2 rounded-lg border border-gray-200 resize-none"
      />
      {error && <p className="text-[12px] text-red-500 mt-1 mb-0">{error}</p>}
      <div className="flex justify-end mt-2">
        <button onClick={submit} disabled={saving}
          className="h-9 px-4 text-[13px] font-semibold text-white rounded-lg border-none disabled:opacity-60"
          style={{ background: 'linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)' }}>
          {saving ? '저장 중...' : editing ? '수정' : '등록'}
        </button>
      </div>
    </div>
  );
};

const ReviewItem = ({ review, canDelete, onDeleted, productId }) => {
  const [error, setError] = useState('');

  const handleDelete = () => {
    if (!window.confirm('이 리뷰를 삭제하시겠습니까?')) return;
    setError('');
    deleteReview(productId, review.reviewId)
      .then(() => onDeleted?.())
      .catch((err) => setError(resolveError(err, '리뷰 삭제에 실패했습니다.')));
  };

  return (
    <div className="bg-white border border-gray-100 rounded-xl p-4">
      <div className="flex items-center justify-between mb-1">
        <div className="flex items-center gap-2">
          <StarRating value={review.rating} size="sm" />
          <span className="text-[12px] text-gray-400">회원 #{review.userId}</span>
        </div>
        <span className="text-[12px] text-gray-300">
          {formatDate(review.updatedAt || review.createdAt)}
          {review.updatedAt ? ' (수정됨)' : ''}
        </span>
      </div>
      {review.content && (
        <p className="text-[13px] text-gray-700 m-0 whitespace-pre-line">{review.content}</p>
      )}
      {canDelete && (
        <div className="flex justify-end mt-2">
          <button onClick={handleDelete}
            className="text-[12px] text-red-500 bg-transparent border-none cursor-pointer p-0">
            삭제
          </button>
        </div>
      )}
      {error && <p className="text-[12px] text-red-500 mt-1 mb-0">{error}</p>}
    </div>
  );
};

export default ReviewSection;
