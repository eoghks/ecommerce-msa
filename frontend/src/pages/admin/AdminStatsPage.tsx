import { useState, useEffect, useCallback } from 'react';
import type { AxiosError } from 'axios';
import {
  getSalesSummary,
  getDailySales,
  getTopProducts,
  getTopSellers,
  getFailedOrderTrend,
} from '../../api/stats';
import TrendBarChart from '../../components/admin/TrendBarChart';
import type { TrendPoint } from '../../components/admin/TrendBarChart';
import type {
  ApiErrorResponse,
  DailySales,
  FailedOrderTrend,
  ProductSales,
  SalesSummary,
  SellerSales,
} from '../../types';

// 빠른 기간 선택 (일수) — 오늘 포함
const QUICK_RANGES = [7, 30, 90];

// 기본 조회 기간 (서버 기본값과 동일한 30일)
const DEFAULT_RANGE_DAYS = 30;

// 최대 조회 기간 (서버 검증값과 동일한 366일)
const MAX_RANGE_DAYS = 366;

const MS_PER_DAY = 86_400_000;

// Top N 조회 개수
const TOP_LIMIT = 10;

const EMPTY_MESSAGE = '해당 기간 데이터가 없습니다.';

// 조회 실패 시 값 자리 표기 — "데이터 없음(0)"과 구분한다
const NO_DATA = '—';

// 축약 표기 단위
const TEN_THOUSAND = 10_000;
const HUNDRED_MILLION = 100_000_000;

interface Period {
  from: string;
  to: string;
}

const isoDate = (date: Date) => {
  const year  = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day   = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

// 시작일·종료일을 포함한 조회 일수 (ISO 날짜 문자열 기준)
const rangeDays = (from: string, to: string) =>
  Math.round((new Date(to).getTime() - new Date(from).getTime()) / MS_PER_DAY) + 1;

// 오늘을 포함한 최근 N일 기간
const recentPeriod = (days: number): Period => {
  const to = new Date();
  const from = new Date();
  from.setDate(to.getDate() - (days - 1));
  return { from: isoDate(from), to: isoDate(to) };
};

const formatNumber = (value: number) => value.toLocaleString('ko-KR');
const formatWon = (value: number) => `${value.toLocaleString('ko-KR')}원`;
const formatCount = (value: number) => `${formatNumber(value)}건`;
const formatRate = (rate: number) => `${(rate * 100).toFixed(2)}%`;

// 소수 1자리까지 유지하고 불필요한 .0 은 제거 — 1.5만 / 2만
const withDecimal = (value: number) => Number(value.toFixed(1)).toLocaleString('ko-KR');

// 만·억 단위 축약 — 차트 Y축처럼 공간이 좁은 곳에만 사용한다(툴팁·KPI는 정확값 표기).
// 만 미만은 원 단위 그대로 표기해 반올림 왜곡(15,000 → "2만")을 없앤다.
const formatCompact = (value: number) => {
  if (value >= HUNDRED_MILLION) return `${withDecimal(value / HUNDRED_MILLION)}억`;
  if (value >= TEN_THOUSAND)    return `${withDecimal(value / TEN_THOUSAND)}만`;
  return formatNumber(value);
};

// 조회 실패로 값이 없으면 "—" — 0원으로 오독되지 않게 한다
const orNoData = (value: number | undefined, format: (value: number) => string) =>
  value === undefined ? NO_DATA : format(value);

const errorDetail = (err: unknown, fallback: string) =>
  (err as AxiosError<ApiErrorResponse>).response?.data?.detail || fallback;

type ChartMetric = 'revenue' | 'orderCount';

// V1.1-7: 관리자 매출 통계 대시보드 (ADMIN 전용)
const AdminStatsPage = () => {
  const [period, setPeriod] = useState<Period>(() => recentPeriod(DEFAULT_RANGE_DAYS));
  const [draft, setDraft] = useState<Period>(() => recentPeriod(DEFAULT_RANGE_DAYS));

  const [summary, setSummary]   = useState<SalesSummary | null>(null);
  const [daily, setDaily]       = useState<DailySales[]>([]);
  const [products, setProducts] = useState<ProductSales[]>([]);
  const [sellers, setSellers]   = useState<SellerSales[]>([]);
  const [failed, setFailed]     = useState<FailedOrderTrend[]>([]);

  const [metric, setMetric]   = useState<ChartMetric>('revenue');
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState('');       // 조회 실패 — 지표를 렌더링하지 않는다
  const [formError, setFormError] = useState('');   // 입력값 검증 실패 — 기존 조회 결과는 유지한다

  const load = useCallback(async ({ from, to }: Period) => {
    setLoading(true);
    setError('');
    setFormError('');
    try {
      const [summaryRes, dailyRes, productRes, sellerRes, failedRes] = await Promise.all([
        getSalesSummary(from, to),
        getDailySales(from, to),
        getTopProducts(from, to, TOP_LIMIT),
        getTopSellers(from, to, TOP_LIMIT),
        getFailedOrderTrend(from, to),
      ]);
      setSummary(summaryRes.data);
      setDaily(dailyRes.data ?? []);
      setProducts(productRes.data ?? []);
      setSellers(sellerRes.data ?? []);
      setFailed(failedRes.data ?? []);
    } catch (err) {
      setError(errorDetail(err, '통계를 불러오지 못했습니다.'));
      // 실패한 응답을 "매출 0"으로 오독하지 않도록 이전 지표를 비운다
      setSummary(null);
      setDaily([]);
      setProducts([]);
      setSellers([]);
      setFailed([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(period); }, [period, load]);

  const applyQuickRange = (days: number) => {
    const next = recentPeriod(days);
    setDraft(next);
    setPeriod(next);
  };

  const applyDraft = () => {
    if (!draft.from || !draft.to) {
      setFormError('조회 기간을 모두 입력해주세요.');
      return;
    }
    // L-4: 서버 왕복 없이 기간 조건을 먼저 검증한다(ISO 문자열은 사전순 비교로 날짜 비교 가능)
    if (draft.from > draft.to) {
      setFormError('조회 시작일이 종료일보다 늦을 수 없습니다.');
      return;
    }
    if (rangeDays(draft.from, draft.to) > MAX_RANGE_DAYS) {
      setFormError(`조회 기간은 최대 ${MAX_RANGE_DAYS}일까지 가능합니다.`);
      return;
    }
    setFormError('');
    setPeriod({ ...draft });
  };

  const trendPoints: TrendPoint[] = daily.map((row) => ({
    date: row.date,
    value: metric === 'revenue' ? row.revenue : row.orderCount,
  }));
  const failedPoints: TrendPoint[] = failed.map((row) => ({ date: row.date, value: row.count }));

  const hasSales = daily.some((row) => row.revenue > 0 || row.orderCount > 0);
  const hasFailed = failed.some((row) => row.count > 0);

  return (
    <div className="max-w-[1100px] mx-auto w-full">
      <h1 className="text-xl font-bold text-gray-900 mb-5 m-0">매출 통계</h1>

      <PeriodSelector
        period={period}
        draft={draft}
        onQuick={applyQuickRange}
        onDraftChange={setDraft}
        onApply={applyDraft}
        disabled={loading}
      />

      {formError && <div className="error-box mb-4">{formError}</div>}

      {loading ? (
        <StatsSkeleton />
      ) : error ? (
        <StatsErrorBox message={error} onRetry={() => load(period)} />
      ) : (
        <>
          <KpiCards summary={summary} />

          <section className="bg-white border border-gray-100 rounded-2xl p-4 mb-4">
            <div className="flex items-center justify-between gap-2 mb-3 flex-wrap">
              <h2 className="text-[15px] font-bold text-gray-900 m-0">일별 추이</h2>
              <div className="flex items-center gap-1.5">
                <MetricButton label="매출" active={metric === 'revenue'}
                  onClick={() => setMetric('revenue')} />
                <MetricButton label="주문수" active={metric === 'orderCount'}
                  onClick={() => setMetric('orderCount')} />
              </div>
            </div>
            {hasSales ? (
              <div className="overflow-x-auto">
                <div className="min-w-[520px]">
                  <TrendBarChart
                    points={trendPoints}
                    formatValue={metric === 'revenue' ? formatCompact : formatNumber}
                    formatTooltip={metric === 'revenue' ? formatWon : formatCount}
                  />
                </div>
              </div>
            ) : (
              <EmptyBox />
            )}
          </section>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-4">
            <TopProductTable rows={products} />
            <TopSellerTable rows={sellers} />
          </div>

          <section className="bg-white border border-gray-100 rounded-2xl p-4">
            <div className="flex items-center justify-between gap-2 mb-3 flex-wrap">
              <h2 className="text-[15px] font-bold text-gray-900 m-0">실패 주문 추이</h2>
              <span className="text-[12px] text-gray-500">
                기간 합계 {formatNumber(summary?.failedOrderCount ?? 0)}건
              </span>
            </div>
            {hasFailed ? (
              <div className="overflow-x-auto">
                <div className="min-w-[520px]">
                  <TrendBarChart points={failedPoints} color="#f59e0b" unit="건" />
                </div>
              </div>
            ) : (
              <EmptyBox message="해당 기간 실패 주문이 없습니다." />
            )}
          </section>
        </>
      )}
    </div>
  );
};

interface PeriodSelectorProps {
  period: Period;
  draft: Period;
  onQuick: (days: number) => void;
  onDraftChange: (period: Period) => void;
  onApply: () => void;
  disabled: boolean;
}

const PeriodSelector = ({ period, draft, onQuick, onDraftChange, onApply, disabled }: PeriodSelectorProps) => (
  <div className="bg-white border border-gray-100 rounded-2xl p-4 mb-4">
    <div className="flex flex-wrap items-center gap-2">
      {QUICK_RANGES.map((days) => {
        const quick = recentPeriod(days);
        const active = period.from === quick.from && period.to === quick.to;
        return (
          <button key={days} onClick={() => onQuick(days)} disabled={disabled}
            className={`h-9 px-3 text-[13px] font-medium rounded-[10px] border transition-colors disabled:opacity-60 ${
              active
                ? 'bg-brand-600 text-white border-transparent'
                : 'bg-white text-gray-700 border-gray-200 hover:bg-gray-50'
            }`}>
            최근 {days}일
          </button>
        );
      })}
      <div className="flex items-center gap-1.5 flex-wrap">
        <input type="date" value={draft.from} max={draft.to} disabled={disabled}
          onChange={(e) => onDraftChange({ ...draft, from: e.target.value })}
          className="h-9 px-2.5 text-[13px] text-gray-700 bg-white border border-gray-200 rounded-[10px]" />
        <span className="text-gray-400 text-[13px]">~</span>
        <input type="date" value={draft.to} min={draft.from} disabled={disabled}
          onChange={(e) => onDraftChange({ ...draft, to: e.target.value })}
          className="h-9 px-2.5 text-[13px] text-gray-700 bg-white border border-gray-200 rounded-[10px]" />
        <button onClick={onApply} disabled={disabled}
          className="h-9 px-4 text-[13px] font-semibold text-white bg-brand-600 rounded-[10px] border-none disabled:opacity-60">
          조회
        </button>
      </div>
    </div>
    <p className="text-[12px] text-gray-400 mt-2 mb-0">
      {period.from} ~ {period.to} (최대 {MAX_RANGE_DAYS}일)
    </p>
  </div>
);

// 값이 없으면(조회 실패) 0 대신 "—" 를 표기한다
const KpiCards = ({ summary }: { summary: SalesSummary | null }) => (
  <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3 mb-4">
    <KpiCard label="유효 매출" value={orNoData(summary?.totalRevenue, formatWon)}
      note="취소 항목 제외" />
    <KpiCard label="주문수" value={orNoData(summary?.orderCount, formatCount)}
      note="확정·부분취소 주문" />
    <KpiCard label="평균 주문금액" value={orNoData(summary?.averageOrderValue, formatWon)}
      note="유효 매출 / 주문수" />
    <KpiCard label="취소율" value={orNoData(summary?.cancelRate, formatRate)}
      note={summary
        ? `전체취소 ${formatNumber(summary.fullyCancelledCount)}건 · 부분취소 ${formatNumber(summary.partiallyCancelledCount)}건`
        : '조회된 데이터 없음'} />
  </div>
);

// 조회 실패 — 지표 대신 에러와 재시도만 노출해 "매출 0" 오독을 막는다
const StatsErrorBox = ({ message, onRetry }: { message: string; onRetry: () => void }) => (
  <div className="bg-white border border-gray-100 rounded-2xl p-4">
    <div className="error-box mb-3">{message}</div>
    <div className="flex items-center justify-center flex-col gap-3 py-10">
      <p className="text-gray-500 text-[13px] m-0">
        통계를 불러오지 못해 지표를 표시할 수 없습니다. (데이터가 0인 것과 다릅니다)
      </p>
      <button onClick={onRetry}
        className="h-9 px-4 text-[13px] font-semibold text-white bg-brand-600 rounded-[10px] border-none">
        다시 시도
      </button>
    </div>
  </div>
);

interface KpiCardProps {
  label: string;
  value: string;
  note: string;
}

const KpiCard = ({ label, value, note }: KpiCardProps) => (
  <div className="bg-white border border-gray-100 rounded-2xl p-4">
    <p className="text-[12px] font-medium text-gray-500 m-0">{label}</p>
    <p className="text-[19px] font-bold text-gray-900 mt-1.5 mb-0 break-all">{value}</p>
    <p className="text-[11px] text-gray-400 mt-1 mb-0">{note}</p>
  </div>
);

const MetricButton = ({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) => (
  <button onClick={onClick}
    className={`h-8 px-3 text-[12px] font-medium rounded-lg border transition-colors ${
      active ? 'bg-brand-600 text-white border-transparent' : 'bg-white text-gray-600 border-gray-200 hover:bg-gray-50'
    }`}>
    {label}
  </button>
);

const TopProductTable = ({ rows }: { rows: ProductSales[] }) => (
  <section className="bg-white border border-gray-100 rounded-2xl p-4">
    <h2 className="text-[15px] font-bold text-gray-900 mb-3 m-0">상품 매출 Top {TOP_LIMIT}</h2>
    {rows.length === 0 ? (
      <EmptyBox />
    ) : (
      <div className="overflow-x-auto">
        <table className="w-full text-[13px] min-w-[360px]">
          <thead>
            <tr className="text-left text-gray-500 border-b border-gray-100">
              <th className="px-2 py-2 font-medium">#</th>
              <th className="px-2 py-2 font-medium">상품</th>
              <th className="px-2 py-2 font-medium text-right">수량</th>
              <th className="px-2 py-2 font-medium text-right">매출</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row, index) => (
              <tr key={row.productId} className="border-b border-gray-50 last:border-0">
                <td className="px-2 py-2 text-gray-400">{index + 1}</td>
                <td className="px-2 py-2 text-gray-900 max-w-[200px]">
                  <span className="block truncate" title={row.productName}>{row.productName}</span>
                </td>
                <td className="px-2 py-2 text-gray-700 text-right">{formatNumber(row.quantity)}</td>
                <td className="px-2 py-2 text-gray-900 font-medium text-right">{formatNumber(row.revenue)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    )}
  </section>
);

const TopSellerTable = ({ rows }: { rows: SellerSales[] }) => (
  <section className="bg-white border border-gray-100 rounded-2xl p-4">
    <h2 className="text-[15px] font-bold text-gray-900 mb-3 m-0">판매자 매출 Top {TOP_LIMIT}</h2>
    {rows.length === 0 ? (
      <EmptyBox />
    ) : (
      <div className="overflow-x-auto">
        <table className="w-full text-[13px] min-w-[360px]">
          <thead>
            <tr className="text-left text-gray-500 border-b border-gray-100">
              <th className="px-2 py-2 font-medium">#</th>
              <th className="px-2 py-2 font-medium">판매자</th>
              <th className="px-2 py-2 font-medium text-right">주문수</th>
              <th className="px-2 py-2 font-medium text-right">매출</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row, index) => (
              <tr key={row.sellerId ?? 'platform'} className="border-b border-gray-50 last:border-0">
                <td className="px-2 py-2 text-gray-400">{index + 1}</td>
                <td className="px-2 py-2 text-gray-900">
                  {row.sellerId === null ? '플랫폼(관리자)' : `판매자 #${row.sellerId}`}
                </td>
                <td className="px-2 py-2 text-gray-700 text-right">{formatNumber(row.orderCount)}</td>
                <td className="px-2 py-2 text-gray-900 font-medium text-right">{formatNumber(row.revenue)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    )}
  </section>
);

const EmptyBox = ({ message = EMPTY_MESSAGE }: { message?: string }) => (
  <div className="flex items-center justify-center py-14">
    <p className="text-gray-400 text-[13px] m-0">{message}</p>
  </div>
);

// 로딩 스켈레톤 — KPI 4장 + 차트 자리
const StatsSkeleton = () => (
  <div>
    <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3 mb-4">
      {[0, 1, 2, 3].map((key) => (
        <div key={key} className="bg-white border border-gray-100 rounded-2xl p-4">
          <div className="h-3 w-16 bg-gray-100 rounded animate-pulse" />
          <div className="h-5 w-28 bg-gray-100 rounded mt-3 animate-pulse" />
        </div>
      ))}
    </div>
    <div className="bg-white border border-gray-100 rounded-2xl p-4">
      <div className="h-[200px] bg-gray-50 rounded animate-pulse" />
    </div>
  </div>
);

export default AdminStatsPage;
