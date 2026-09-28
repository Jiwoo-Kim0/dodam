import type {
  AccountApiResponse,
  AccountTransferApiResponse,
  AccountTransferRequest,
  AccountBalanceSummaryApiResponse,
  AccountSummaryApiResponse,
  CardApiResponse,
  CardPaymentSchedulesApiResponse,
  DepositSavingApiResponse,
  DepositSavingSummaryApiResponse,
  InstallmentApiResponse,
  InstallmentScheduleApiResponse,
  InactiveAccountsApiResponse,
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

interface GetInstallmentsParams {
  cardId?: number;
  status?: 'ACTIVE' | 'COMPLETED' | 'CANCELED' | 'OVERDUE';
  page?: number;
  size?: number;
}

interface GetInstallmentScheduleParams {
  startMonth?: string;
  endMonth?: string;
}

export const assetApi = {
  getAccounts: () => apiClient.get<AccountApiResponse[]>('/v1/accounts'),

  getAccountSummary: () =>
    apiClient.get<AccountSummaryApiResponse>('/v1/accounts/summary'),

  getAccountBalanceSummary: () =>
    apiClient.get<AccountBalanceSummaryApiResponse>('/v1/accounts/balance-summary'),

  getInactiveAccounts: () =>
    apiClient.get<InactiveAccountsApiResponse>('/v1/accounts/inactive'),

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

  getDepositSavingSummary: () =>
    apiClient.get<DepositSavingSummaryApiResponse>('/v1/deposit-savings/summary'),

  getInstallments: ({
    cardId,
    status,
    page = 0,
    size = 100,
  }: GetInstallmentsParams = {}) =>
    apiClient.get<PageResponse<InstallmentApiResponse>>('/v1/installments', {
      query: { cardId, status, page, size },
    }),

  getInstallmentSchedule: ({
    startMonth,
    endMonth,
  }: GetInstallmentScheduleParams = {}) =>
    apiClient.get<InstallmentScheduleApiResponse>('/v1/installments/monthly-schedule', {
      query: { startMonth, endMonth },
    }),

  /**
   * 계좌 간 이체. 목표 통장 보내기와 되가져오기가 두 id를 맞바꿔 같은 함수를 쓴다.
   * **실제 돈이 움직인다.** 응답을 받기 전에 화면을 미리 바꾸지 않는다.
   */
  transfer: (request: AccountTransferRequest) =>
    apiClient.post<AccountTransferApiResponse>('/v1/accounts/transfer', {
      body: request,
    }),
};
