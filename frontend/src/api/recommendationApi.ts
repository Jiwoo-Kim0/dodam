import type {
  RecommendationApiResponse,
  RecommendationGenerationApiResponse,
  RecommendationSummaryApiResponse,
} from '@/types/discoveryApi.types';

import type { PageResponse } from './api.types';
import { apiClient } from './apiClient';

const PATH = '/v1/recommendations';

export const recommendationApi = {
  generate: (diagnosisId: number) =>
    apiClient.post<RecommendationGenerationApiResponse>(`${PATH}/generate`, {
      body: { diagnosisId },
    }),
  /** 알림 바로가기가 원천 추천 ID로 진단 문제를 찾을 때 쓴다. */
  getRecommendation: (recommendationId: number) =>
    apiClient.get<RecommendationApiResponse>(`${PATH}/${recommendationId}`),
  getRecommendations: (page = 0) =>
    apiClient.get<PageResponse<RecommendationApiResponse>>(PATH, {
      query: { page, size: 100 },
    }),
  getSummary: () =>
    apiClient.get<RecommendationSummaryApiResponse>(`${PATH}/summary`),
};
