import type {
  CoachChatRequest,
  CoachChatResponse,
  CoachMessageResponse,
  FinancialSummaryRequest,
  InefficiencyExplanationRequest,
  RecommendationExplanationRequest,
} from '@/types/coachApi.types';

import { apiClient } from './apiClient';

const COACH_PATH = '/v1/coach';

/**
 * 채팅은 도구 실행 + 문장 생성까지 8~15초가 정상이다(계약 §13-3). 기본 타임아웃(15초)으로는
 * 정상 응답도 끊기므로 넉넉히 잡는다.
 */
const CHAT_TIMEOUT_MS = 40_000;

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

  /** 토리에게 묻기 한 턴. `signal`로 화면을 벗어날 때 끊을 수 있다. */
  chat: (request: CoachChatRequest, signal?: AbortSignal) =>
    apiClient.post<CoachChatResponse>(`${COACH_PATH}/chat`, {
      body: request,
      signal,
      timeoutMs: CHAT_TIMEOUT_MS,
    }),
};
