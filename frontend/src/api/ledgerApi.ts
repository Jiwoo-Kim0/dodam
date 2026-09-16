import { apiClient } from './apiClient';
import type { PageResponse } from './api.types';

import type {
  CategorySpendingApiResponse,
  CreateManualTransactionRequest,
  CategorySpendingTrendApiResponse,
  DailySpendingApiResponse,
  PaymentMethodSpendingApiResponse,
  SavingRatioApiResponse,
  SpendingAnomalyApiResponse,
  SpendingMonthlySummaryApiResponse,
  SpendingTypeBreakdownApiResponse,
  TransactionApiResponse,
  TransactionCategoryApiResponse,
  UpdateManualTransactionRequest,
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

  getTransaction: (transactionId: string) =>
    apiClient.get<TransactionApiResponse>(`/v1/transactions/${transactionId}`),

  updateTransactionCategory: (transactionId: string, categoryId: number) =>
    apiClient.patch<TransactionApiResponse>(
      `/v1/transactions/${transactionId}/category`,
      { body: { categoryId } },
    ),

  updateTransactionMemo: (transactionId: string, memo: string) =>
    apiClient.patch<TransactionApiResponse>(
      `/v1/transactions/${transactionId}/memo`,
      { body: { memo } },
    ),

  updateTransactionVisibility: (transactionId: string, hidden: boolean) =>
    apiClient.patch<TransactionApiResponse>(
      `/v1/transactions/${transactionId}/visibility`,
      { body: { hidden } },
    ),

  createManualTransaction: (request: CreateManualTransactionRequest) =>
    apiClient.post<TransactionApiResponse>('/v1/transactions', { body: request }),

  updateManualTransaction: (
    transactionId: string,
    request: UpdateManualTransactionRequest,
  ) =>
    apiClient.put<TransactionApiResponse>(`/v1/transactions/${transactionId}`, {
      body: request,
    }),

  deleteManualTransaction: async (transactionId: string) => {
    await apiClient.delete<void>(`/v1/transactions/${transactionId}`);
    return true;
  },

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
