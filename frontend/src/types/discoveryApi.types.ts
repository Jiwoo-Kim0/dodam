import type { SimulationType } from '@/types/simulationApi.types';

export interface DiagnosisApiResponse {
  diagnosisId: number;
  goalId: number | null;
  issueCount: number;
  completedAt: string;
}

export interface DiagnosisRunApiResponse {
  diagnosis: DiagnosisApiResponse;
  cached: boolean;
}

export interface DiagnosisIssueApiResponse {
  issueId: number;
  diagnosisId: number;
  domain: string;
  type: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  title: string;
  description: string;
}

export interface RecommendationEffectApiResponse {
  available: boolean;
  monthlyCostSaving: number | null;
  annualCostSaving: number | null;
  interestSaving: number | null;
  advancedDays: number | null;
}

/** 프리셋이 조립해 주는 명세 17 요청 본문. 유형마다 쓰는 키가 다르다. */
export interface RecommendationSimulationRequestApiResponse {
  category?: string | null;
  monthlyReduction?: number | null;
  accountNo?: string | null;
  goalId?: number | null;
  monthlySavingAmount?: number | null;
  targetProductId?: number | null;
  refinanceAmount?: number | null;
  durationMonths?: number | null;
}

export type RecommendationSimulationUnavailableReason =
  | 'NO_MATCHING_SIMULATION_TYPE'
  | 'EFFECT_AMOUNT_MISSING'
  | 'SNAPSHOT_FIELD_MISSING'
  | 'REFINANCE_TARGET_MISSING';

/**
 * 587 시뮬레이션 프리셋. 저장된 추천·근거를 명세 17 요청 본문 모양으로 재배열한 실행 제안이며
 * 재계산이 아니다. FE는 `request`를 그대로, 또는 사용자가 고친 값으로 `endpoint`에 POST한다.
 * `available`이 false면 `type`·`endpoint`·`request` 키가 없고 `unavailableReason`이 있다.
 */
export interface RecommendationSimulationApiResponse {
  available: boolean;
  type?: SimulationType;
  endpoint?: string;
  request?: RecommendationSimulationRequestApiResponse;
  assumptions?: readonly string[];
  unavailableReason?: RecommendationSimulationUnavailableReason | null;
}

export interface RecommendationApiResponse {
  recommendationId: number;
  diagnosisId: number;
  /** 628 이전 추천과 여러 문제를 합친 추천은 null이다. */
  sourceIssueId: number | null;
  domain: string;
  type: string;
  title: string;
  description: string;
  priority: number;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'DEFERRED' | 'COMPLETED';
  effect: RecommendationEffectApiResponse;
  /** 587. 배포 이전 백엔드를 대비해 선택 필드로 둔다. */
  simulation?: RecommendationSimulationApiResponse;
}

export interface RecommendationGenerationApiResponse {
  diagnosisId: number;
  created: number;
  reused: number;
}
