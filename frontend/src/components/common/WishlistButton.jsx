import { useNavigate } from 'react-router-dom';
import useAuthStore from '../../store/authStore';
import useWishlistStore from '../../store/wishlistStore';

/**
 * 찜 하트 토글 버튼.
 * - 비로그인 시 로그인 페이지로 유도.
 * - 클릭 시 낙관적 업데이트(스토어에서 처리).
 * @param {number} productId 상품 ID
 * @param {'card'|'detail'} variant 표시 위치별 스타일
 */
const WishlistButton = ({ productId, variant = 'card' }) => {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuthStore();
  const isWished = useWishlistStore((s) => s.ids.has(productId));
  const add = useWishlistStore((s) => s.add);
  const remove = useWishlistStore((s) => s.remove);

  const handleToggle = async (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (!isAuthenticated) {
      navigate('/login', { state: { from: `/products/${productId}` } });
      return;
    }
    try {
      if (isWished) await remove(productId);
      else await add(productId);
    } catch {
      // 낙관적 업데이트가 스토어에서 원복됨 — 별도 처리 없음
    }
  };

  const isCard = variant === 'card';
  const size = isCard ? 18 : 22;

  return (
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
  );
};

export default WishlistButton;
