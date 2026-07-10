import { useState } from 'react';

const MAX_STARS = 5;

/**
 * 별점 표시/입력 컴포넌트 (V1.1-1).
 * - readOnly: 평균 별점 표시 (소수점 반올림 표시)
 * - interactive(onChange 제공): 클릭으로 1~5 선택
 * @param {number} value 현재 별점(0~5)
 * @param {(rating:number)=>void} [onChange] 선택 콜백 — 있으면 입력 모드
 * @param {'sm'|'md'|'lg'} [size] 크기
 */
const StarRating = ({ value = 0, onChange, size = 'md' }) => {
  const [hover, setHover] = useState(0);
  const interactive = typeof onChange === 'function';
  const px = size === 'lg' ? 22 : size === 'sm' ? 14 : 18;
  const shown = hover || value;

  return (
    <span className="inline-flex items-center gap-0.5" role={interactive ? 'radiogroup' : 'img'}>
      {Array.from({ length: MAX_STARS }, (_, i) => {
        const filled = i < Math.round(shown);
        return (
          <button
            key={i}
            type="button"
            disabled={!interactive}
            onClick={() => interactive && onChange(i + 1)}
            onMouseEnter={() => interactive && setHover(i + 1)}
            onMouseLeave={() => interactive && setHover(0)}
            aria-label={`${i + 1}점`}
            className={`bg-transparent border-none p-0 leading-none ${interactive ? 'cursor-pointer' : 'cursor-default'}`}
            style={{ color: filled ? '#f59e0b' : '#d1d5db', fontSize: px }}
          >
            ★
          </button>
        );
      })}
    </span>
  );
};

export default StarRating;
