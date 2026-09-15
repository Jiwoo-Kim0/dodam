import type {
  CashflowEventListApiResponse,
  CashflowRiskListApiResponse,
} from '@/types/cashflowApi.types';

import { apiClient } from './apiClient';

interface CashflowPeriodParams {
  startDate?: string;
  endDate?: string;
}

export const cashflowApi = {
  getEvents: ({ startDate, endDate }: CashflowPeriodParams = {}) =>
    apiClient.get<CashflowEventListApiResponse>('/v1/cashflow/events', {
      query: { startDate, endDate },
    }),

  getRisks: () =>
    apiClient.get<CashflowRiskListApiResponse>('/v1/cashflow/risks'),
};
