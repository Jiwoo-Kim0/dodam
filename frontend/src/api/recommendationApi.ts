import type {
  RecommendationApiResponse,
  RecommendationGenerationApiResponse,
} from '@/types/discoveryApi.types';

import type { PageResponse } from './api.types';
import { apiClient } from './apiClient';

const PATH = '/v1/recommendations';

export const recommendationApi = {
  generate: (diagnosisId: number) =>
    apiClient.post<RecommendationGenerationApiResponse>(`${PATH}/generate`, {
      body: { diagnosisId },
    }),
  getRecommendations: (page = 0) =>
    apiClient.get<PageResponse<RecommendationApiResponse>>(PATH, {
      query: { page, size: 100 },
    }),
};
