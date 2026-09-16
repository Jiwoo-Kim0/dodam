import type { PageResponse } from '@/api/api.types';
import type {
  CreateGoalRequest,
  GoalAccountsApiResponse,
  GoalApiResponse,
  GoalForecastApiResponse,
  GoalProgressApiResponse,
  GoalRiskApiResponse,
  RequiredSavingApiResponse,
  SpendingGoalProgressApiResponse,
  UpdateGoalAccountsRequest,
  UpdateGoalRequest,
} from '@/types/goalApi.types';

import { apiClient } from './apiClient';

interface GetGoalsParams {
  status?: string;
  type?: string;
  page?: number;
  size?: number;
}

interface GetGoalAccountsParams {
  page?: number;
  size?: number;
}

interface GetGoalForecastParams {
  baseDate?: string;
  months?: number;
}

export const goalApi = {
  getGoals: ({ status, type, page = 0, size = 20 }: GetGoalsParams = {}) =>
    apiClient.get<PageResponse<GoalApiResponse>>('/v1/goals', {
      query: { status, type, page, size },
    }),

  createGoal: (request: CreateGoalRequest) =>
    apiClient.post<GoalApiResponse>('/v1/goals', { body: request }),

  getGoal: (goalId: number) =>
    apiClient.get<GoalApiResponse>(`/v1/goals/${goalId}`),

  updateGoal: (goalId: number, request: UpdateGoalRequest) =>
    apiClient.patch<GoalApiResponse>(`/v1/goals/${goalId}`, {
      body: request,
    }),

  deleteGoal: (goalId: number) =>
    apiClient.delete<void>(`/v1/goals/${goalId}`),

  getGoalAccounts: (
    goalId: number,
    { page = 0, size = 20 }: GetGoalAccountsParams = {},
  ) =>
    apiClient.get<GoalAccountsApiResponse>(`/v1/goals/${goalId}/accounts`, {
      query: { page, size },
    }),

  replaceGoalAccounts: (
    goalId: number,
    request: UpdateGoalAccountsRequest,
  ) =>
    apiClient.put<GoalAccountsApiResponse>(`/v1/goals/${goalId}/accounts`, {
      body: request,
    }),

  getProgress: (goalId: number) =>
    apiClient.get<GoalProgressApiResponse>(`/v1/goals/${goalId}/progress`),

  getRequiredSaving: (goalId: number) =>
    apiClient.get<RequiredSavingApiResponse>(
      `/v1/goals/${goalId}/required-saving`,
    ),

  getForecast: (
    goalId: number,
    { baseDate, months }: GetGoalForecastParams = {},
  ) =>
    apiClient.get<GoalForecastApiResponse>(`/v1/goals/${goalId}/forecast`, {
      query: { baseDate, months },
    }),

  getSpendingProgress: (goalId: number) =>
    apiClient.get<SpendingGoalProgressApiResponse>(
      `/v1/goals/${goalId}/spending-progress`,
    ),

  getRisk: (goalId: number) =>
    apiClient.get<GoalRiskApiResponse>(`/v1/goals/${goalId}/risk`),
};
