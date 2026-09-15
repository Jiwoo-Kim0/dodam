import { apiClient } from './apiClient';
import type { PageResponse } from './api.types';

import type {
  CategorySpendingApiResponse,
  CategorySpendingTrendApiResponse,
  DailySpendingApiResponse,
  PaymentMethodSpendingApiResponse,
  SavingRatioApiResponse,
  SpendingAnomalyApiResponse,
  SpendingMonthlySummaryApiResponse,
  SpendingTypeBreakdownApiResponse,
  TransactionApiResponse,
  TransactionCategoryApiResponse,
} from '@/types/ledgerApi.types';

interface GetTransactionsParams {
  startDate?: string;
  endDate?: string;
  category?: string;
  paymentMethod?: string;
  hidden?: boolean;
  page?: number;
  size?: number;
}

interface GetDailySpendingParams {
  startDate?: string;
  endDate?: string;
}

export const ledgerApi = {
  getTransactions: ({
    startDate,
    endDate,
    category,
    paymentMethod,
    hidden,
    page = 0,
    size = 100,
  }: GetTransactionsParams = {}) =>
    apiClient.get<PageResponse<TransactionApiResponse>>('/v1/transactions', {
      query: {
        startDate,
        endDate,
        category,
        paymentMethod,
        hidden,
        page,
        size,
      },
    }),

  getTransactionCategories: () =>
    apiClient.get<TransactionCategoryApiResponse[]>('/v1/transactions/categories'),

  getMonthlySummary: (yearMonth?: string) =>
    apiClient.get<SpendingMonthlySummaryApiResponse>('/v1/spending/monthly-summary', {
      query: { yearMonth },
    }),

  getDailySpending: ({
    startDate,
    endDate,
  }: GetDailySpendingParams = {}) =>
    apiClient.get<DailySpendingApiResponse>('/v1/spending/daily', {
      query: { startDate, endDate },
    }),

  getCategorySpending: (yearMonth?: string) =>
    apiClient.get<CategorySpendingApiResponse>('/v1/spending/categories', {
      query: { yearMonth },
    }),

  getCategoryTrend: (startMonth: string, endMonth: string) =>
    apiClient.get<CategorySpendingTrendApiResponse>(
      '/v1/spending/categories/trend',
      { query: { startMonth, endMonth } },
    ),

  getSavingRatio: (yearMonth: string) =>
    apiClient.get<SavingRatioApiResponse>('/v1/spending/saving-ratio', {
      query: { yearMonth },
    }),

  getFixedSpending: (startMonth: string, endMonth: string) =>
    apiClient.get<SpendingTypeBreakdownApiResponse>('/v1/spending/fixed', {
      query: { startMonth, endMonth },
    }),

  getVariableSpending: (startMonth: string, endMonth: string) =>
    apiClient.get<SpendingTypeBreakdownApiResponse>('/v1/spending/variable', {
      query: { startMonth, endMonth },
    }),

  getPaymentMethodSpending: (yearMonth: string) =>
    apiClient.get<PaymentMethodSpendingApiResponse>(
      '/v1/spending/payment-methods',
      { query: { yearMonth } },
    ),

  getSpendingAnomalies: (startMonth: string, endMonth: string) =>
    apiClient.get<SpendingAnomalyApiResponse>('/v1/spending/anomalies', {
      query: { startMonth, endMonth },
    }),
};
