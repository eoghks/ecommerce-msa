import type { AxiosResponse } from 'axios';
import api from './axios';
import type {
  DailySales,
  FailedOrderTrend,
  ProductSales,
  SalesSummary,
  SellerSales,
} from '../types';

// V1.1-7: 관리자 매출 통계 (ADMIN 전용) — 기간은 ISO date(YYYY-MM-DD), to 포함
const STATS_BASE = '/api/v1/admin/stats';

// Top N 기본 개수 (서버 기본값과 동일)
const DEFAULT_TOP_LIMIT = 10;

export const getSalesSummary = (from: string, to: string): Promise<AxiosResponse<SalesSummary>> =>
  api.get(`${STATS_BASE}/summary`, { params: { from, to } });

export const getDailySales = (from: string, to: string): Promise<AxiosResponse<DailySales[]>> =>
  api.get(`${STATS_BASE}/daily`, { params: { from, to } });

export const getTopProducts = (
  from: string,
  to: string,
  limit = DEFAULT_TOP_LIMIT,
): Promise<AxiosResponse<ProductSales[]>> =>
  api.get(`${STATS_BASE}/products`, { params: { from, to, limit } });

export const getTopSellers = (
  from: string,
  to: string,
  limit = DEFAULT_TOP_LIMIT,
): Promise<AxiosResponse<SellerSales[]>> =>
  api.get(`${STATS_BASE}/sellers`, { params: { from, to, limit } });

export const getFailedOrderTrend = (
  from: string,
  to: string,
): Promise<AxiosResponse<FailedOrderTrend[]>> =>
  api.get(`${STATS_BASE}/failed-orders`, { params: { from, to } });
