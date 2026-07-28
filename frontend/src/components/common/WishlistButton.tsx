import { useState, type MouseEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import type { AxiosError } from 'axios';
import useAuthStore from '../../store/authStore';
import useWishlistStore from '../../store/wishlistStore';
import type { ApiErrorResponse } from '../../types';

// 서버 실패 메시지 추출 — ProblemDetail(detail) 우선, 없으면 기본 문구
const resolveErrorMessage = (err: unknown, fallback: string): string =>
  (err as AxiosError<ApiErrorResponse>)?.response?.data?.detail || fallback;

interface WishlistButtonProps {
  productId: number;
  variant?: 'card' | 'detail';
}

/**
 * 찜 하트 토글 버튼.
 * - 비로그인 시 로그인 페이지로 유도.
 * - 클릭 시 낙관적 업데이트(스토어에서 처리).
 */
const WishlistButton = ({ productId, variant = 'card' }: WishlistButtonProps) => {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuthStore();
  const isWished = useWishlistStore((s) => s.ids.has(productId));
  const add = useWishlistStore((s) => s.add);
  const remove = useWishlistStore((s) => s.remove);
  const [errorMsg, setErrorMsg] = useState('');

  const handleToggle = async (e: MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    e.stopPropagation();
    if (!isAuthenticated) {
      navigate('/login', { state: { from: `/products/${productId}` } });
      return;
    }
    setErrorMsg('');
    try {
      if (isWished) await remove(productId);
      else await add(productId);
    } catch (err) {
      // 낙관적 업데이트는 스토어에서 원복됨 — 사용자에게 실패 사유 노출
      // 판매금지 상품 찜 시 백엔드 400(ProductNotOnSaleException) 메시지 표시
      const fallback = isWished ? '찜 해제에 실패했습니다.' : '찜하기에 실패했습니다.';
      setErrorMsg(resolveErrorMessage(err, fallback));
    }
  };

  const isCard = variant === 'card';
  const size = isCard ? 18 : 22;

  return (
    <span className={isCard ? 'contents' : 'relative inline-flex'}>
      <button
        onClick={handleToggle}
        title={isWished ? '찜 해제' : '찜하기'}
        aria-label={isWished ? '찜 해제' : '찜하기'}
        className={
          isCard
            ? 'absolute top-3 right-3 w-9 h-9 rounded-full flex items-center justify-center bg-white/90 border-none shadow-md transition-transform duration-150 hover:scale-110'
            : 'w-11 h-11 rounded-full flex items-center justify-center border border-gray-200 bg-white transition-colors duration-150 hover:bg-gray-50'
        }
      >
        <svg
          width={size}
          height={size}
          viewBox="0 0 24 24"
          fill={isWished ? '#ef4444' : 'none'}
          stroke={isWished ? '#ef4444' : '#6b7280'}
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
        </svg>
      </button>

      {/* 실패 피드백 — 판매금지 상품 찜 등 백엔드 오류 메시지 노출 */}
      {errorMsg && (
        <span
          role="alert"
          className="absolute top-full right-0 mt-1 z-10 whitespace-nowrap rounded-md bg-red-600 px-2 py-1 text-[11px] font-medium text-white shadow-lg"
        >
          {errorMsg}
        </span>
      )}
    </span>
  );
};

export default WishlistButton;
