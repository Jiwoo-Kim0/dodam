import type {
  CoachMessageResponse,
  FinancialSummaryRequest,
  InefficiencyExplanationRequest,
  RecommendationExplanationRequest,
} from '@/types/coachApi.types';

import { apiClient } from './apiClient';

const COACH_PATH = '/v1/coach';

export const coachApi = {
  getFinancialSummary: (request: FinancialSummaryRequest = {}) =>
    apiClient.post<CoachMessageResponse>(`${COACH_PATH}/financial-summary`, {
      body: request,
    }),

  getInefficiencyExplanation: (request: InefficiencyExplanationRequest) =>
    apiClient.post<CoachMessageResponse>(
      `${COACH_PATH}/inefficiency-explanation`,
      { body: request },
    ),

  getRecommendationExplanation: (request: RecommendationExplanationRequest) =>
    apiClient.post<CoachMessageResponse>(
      `${COACH_PATH}/recommendation-explanation`,
      { body: request },
    ),
};
