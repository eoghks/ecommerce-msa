import api from './axios';

// 서비스별 헬스 상태 조회 (ADMIN)
export const getServicesHealth = () =>
  api.get('/api/v1/monitoring/services');
