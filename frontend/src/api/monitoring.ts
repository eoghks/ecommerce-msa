import type { AxiosResponse } from 'axios';
import api from './axios';
import type { ServiceHealth } from '../types';

// 서비스별 헬스 상태 조회 (ADMIN)
export const getServicesHealth = (): Promise<AxiosResponse<ServiceHealth[]>> =>
  api.get('/api/v1/monitoring/services');
