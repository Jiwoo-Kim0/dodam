import type { PageResponse } from '@/api/api.types';
import type {
  GoalApiResponse,
  SpendingGoalProgressApiResponse,
} from '@/types/goalApi.types';

import { apiClient } from './apiClient';

interface GetGoalsParams {
  status?: string;
  type?: string;
  page?: number;
  size?: number;
}

export const goalApi = {
  getGoals: ({ status, type, page = 0, size = 20 }: GetGoalsParams = {}) =>
    apiClient.get<PageResponse<GoalApiResponse>>('/v1/goals', {
      query: { status, type, page, size },
    }),

  getSpendingProgress: (goalId: number) =>
    apiClient.get<SpendingGoalProgressApiResponse>(
      `/v1/goals/${goalId}/spending-progress`,
    ),
};
