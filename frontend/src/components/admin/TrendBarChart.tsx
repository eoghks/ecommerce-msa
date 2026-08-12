// V1.1-7: 통계 추이용 경량 SVG 막대 차트 — 외부 차트 라이브러리 없이 구현
// viewBox 기준으로 그려 컨테이너 폭에 맞춰 늘어나고, 좁은 화면에서는 부모의 가로 스크롤로 처리한다.

/** 차트 한 점 — 날짜(YYYY-MM-DD)와 값 */
export interface TrendPoint {
  date: string;
  value: number;
}

interface TrendBarChartProps {
  points: TrendPoint[];
  /** 막대 색상 */
  color?: string;
  /** 값 표기 형식 (툴팁·Y축 라벨) */
  formatValue?: (value: number) => string;
  /** 값 단위 설명 — 툴팁에 붙는다 */
  unit?: string;
}

const VIEW_WIDTH = 640;
const VIEW_HEIGHT = 200;
const PAD_TOP = 10;
const PAD_RIGHT = 8;
const PAD_BOTTOM = 22;
const PAD_LEFT = 56;

// X축 날짜 라벨은 최대 6개만 표기 (좁은 화면 겹침 방지)
const MAX_X_LABELS = 6;

const PLOT_WIDTH = VIEW_WIDTH - PAD_LEFT - PAD_RIGHT;
const PLOT_HEIGHT = VIEW_HEIGHT - PAD_TOP - PAD_BOTTOM;

// Y축 기준선 비율 (0% · 50% · 100%)
const GRID_RATIOS = [0, 0.5, 1];

const defaultFormat = (value: number) => value.toLocaleString('ko-KR');

const shortDate = (date: string) => date.slice(5);   // MM-DD

const TrendBarChart = ({
  points,
  color = '#4f46e5',
  formatValue = defaultFormat,
  unit = '',
}: TrendBarChartProps) => {
  const maxValue = points.reduce((max, point) => Math.max(max, point.value), 0);
  const slotWidth = PLOT_WIDTH / Math.max(points.length, 1);
  const barWidth = Math.max(slotWidth * 0.62, 1);
  const labelStep = Math.max(1, Math.ceil(points.length / MAX_X_LABELS));

  const barHeight = (value: number) => (maxValue === 0 ? 0 : (value / maxValue) * PLOT_HEIGHT);

  return (
    <svg
      viewBox={`0 0 ${VIEW_WIDTH} ${VIEW_HEIGHT}`}
      className="w-full h-[200px]"
      role="img"
      aria-label="일별 추이 차트"
    >
      {/* Y축 기준선 + 라벨 */}
      {GRID_RATIOS.map((ratio) => {
        const y = PAD_TOP + PLOT_HEIGHT * (1 - ratio);
        return (
          <g key={ratio}>
            <line x1={PAD_LEFT} y1={y} x2={VIEW_WIDTH - PAD_RIGHT} y2={y}
              stroke="#e5e7eb" strokeWidth="1" />
            <text x={PAD_LEFT - 6} y={y + 3} textAnchor="end" fontSize="9" fill="#9ca3af">
              {formatValue(Math.round(maxValue * ratio))}
            </text>
          </g>
        );
      })}

      {/* 막대 */}
      {points.map((point, index) => {
        const height = barHeight(point.value);
        const x = PAD_LEFT + slotWidth * index + (slotWidth - barWidth) / 2;
        const y = PAD_TOP + PLOT_HEIGHT - height;
        return (
          <g key={point.date}>
            <rect x={x} y={y} width={barWidth} height={height} rx="1.5" fill={color}>
              <title>{`${point.date} · ${formatValue(point.value)}${unit}`}</title>
            </rect>
            {index % labelStep === 0 && (
              <text x={x + barWidth / 2} y={VIEW_HEIGHT - 6} textAnchor="middle"
                fontSize="9" fill="#9ca3af">
                {shortDate(point.date)}
              </text>
            )}
          </g>
        );
      })}
    </svg>
  );
};

export default TrendBarChart;
