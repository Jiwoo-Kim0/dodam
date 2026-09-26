import { isApiError } from './ApiError';
import { apiClient } from './apiClient';

export interface SafeboxAccountResponse {
  accountId: number;
  accountNoMasked: string;
  balance: number;
  allocatedAmount: number;
  availableAmount: number;
  shortfallAmount: number;
}

export interface SafeboxAllocationResponse {
  goalId: number;
  accountId: number;
  allocatedAmount: number;
  accountBalance: number;
  availableAmount: number;
  shortfallAmount: number;
}

export const safeboxApi = {
  getAccount: () => apiClient.get<SafeboxAccountResponse>('/v1/safebox/account'),
  /** 전용 계좌가 아직 없으면(404) null. */
  getAccountOrNull: async () => {
    try {
      return await apiClient.get<SafeboxAccountResponse>('/v1/safebox/account');
    } catch (error) {
      if (isApiError(error) && error.status === 404) return null;
      throw error;
    }
  },
  /** 전용 계좌가 있으면 그 계좌를, 없으면 새로 열어 돌려준다(가입 때 개설이 빠진 사용자 보충). */
  openAccount: () => apiClient.post<SafeboxAccountResponse>('/v1/safebox/account'),
  getGoal: (goalId: number) =>
    apiClient.get<SafeboxAllocationResponse>(`/v1/safebox/goals/${goalId}`),
  setGoalAmount: (goalId: number, amount: number) =>
    apiClient.put<SafeboxAllocationResponse>(`/v1/safebox/goals/${goalId}`, {
      body: { amount },
    }),
};
