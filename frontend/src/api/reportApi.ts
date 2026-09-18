import type { MonthlyReportApiResponse } from '@/types/reportApi.types';

import { apiClient } from './apiClient';

export const reportApi = {
  /**
   * 월간 리포트를 조회한다. 저장된 리포트가 없으면 서버가 만들어서 돌려주므로 404가 아니다.
   * `yearMonth`를 비우면 직전 완료 월이 기준이다.
   */
  getMonthly: (yearMonth?: string) =>
    apiClient.get<MonthlyReportApiResponse>('/v1/reports/monthly', {
      query: { yearMonth },
    }),
};
