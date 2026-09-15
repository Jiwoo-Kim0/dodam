import type {
  AccountApiResponse,
  AccountSummaryApiResponse,
  CardApiResponse,
  CardPaymentSchedulesApiResponse,
  DepositSavingApiResponse,
  LoanApiResponse,
  LoanSummaryApiResponse,
} from '@/types/assetApi.types';

import { apiClient } from './apiClient';
import type { PageResponse } from './api.types';

interface GetDepositSavingsParams {
  type?: 'DEPOSIT' | 'SAVINGS';
  status?: 'ACTIVE' | 'INACTIVE' | 'CLOSED';
  page?: number;
  size?: number;
}

interface GetCardPaymentSchedulesParams {
  startDate?: string;
  endDate?: string;
}

export const assetApi = {
  getAccounts: () => apiClient.get<AccountApiResponse[]>('/v1/accounts'),

  getAccountSummary: () =>
    apiClient.get<AccountSummaryApiResponse>('/v1/accounts/summary'),

  getCards: () => apiClient.get<CardApiResponse[]>('/v1/cards'),

  getCardPaymentSchedules: ({
    startDate,
    endDate,
  }: GetCardPaymentSchedulesParams = {}) =>
    apiClient.get<CardPaymentSchedulesApiResponse>('/v1/cards/payment-schedules', {
      query: { startDate, endDate },
    }),

  getLoans: () => apiClient.get<LoanApiResponse[]>('/v1/loans'),

  getLoanSummary: () =>
    apiClient.get<LoanSummaryApiResponse>('/v1/loans/summary'),

  getDepositSavings: ({
    type,
    status,
    page = 0,
    size = 100,
  }: GetDepositSavingsParams = {}) =>
    apiClient.get<PageResponse<DepositSavingApiResponse>>('/v1/deposit-savings', {
      query: { type, status, page, size },
    }),
};
