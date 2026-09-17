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
  /** 규칙별 구조가 다른 진단 스냅샷. 소비 증가 카드에서 수치를 읽는다. */
  currentValue?: Record<string, unknown> | null;
  /** 진단 시점의 비교 기준. 소비 증가 상세에서 이전 월평균과 기간을 읽는다. */
  benchmarkValue?: Record<string, unknown> | null;
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
  loanId?: number | null;
  refinanceAmount?: number | null;
  durationMonths?: number | null;
}

export type RecommendationSimulationUnavailableReason = string;

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

export interface RecommendationDiscoverySummaryApiResponse {
  diagnosisId: number;
  issueCount: number;
  openRecommendationCount: number;
  expectedAnnualCostSaving: number | null;
  expectedGoalAdvanceDays: number | null;
  notes: readonly string[];
}

export interface RecommendationSummaryApiResponse {
  total: number;
  byStatus: Readonly<Record<string, number>>;
  byDomain: Readonly<Record<string, number>>;
  expectedMonthlyCostSaving: number;
  expectedContributionAmount: number;
  discovery: RecommendationDiscoverySummaryApiResponse | null;
}

/**
 * 명세 14 목표 영향(`GET /v1/diagnoses/{id}/goal-impact`). 모든 값은 진단 시점 스냅샷이며
 * 조회 시점으로 재계산하지 않는다. SAVING 목표만 예상 달성일 관련 필드가 찬다.
 * v3 이전 스냅샷은 forecast가 없어 전부 null이고 `coverage`에 SNAPSHOT_WITHOUT_FORECAST가 남는다.
 */
export interface DiagnosisGoalImpactItemApiResponse {
  goalId: number | null;
  name: string | null;
  type: string | null;
  status: string | null;
  /** 예상 달성일 − 목표일. 양수면 지연, 예상일이 없으면 null. */
  daysDifference: number | null;
  targetDate: string | null;
  expectedAchievementDate: string | null;
  onSchedule: boolean | null;
  coverage: readonly string[];
}

export interface DiagnosisGoalImpactApiResponse {
  diagnosisId: number;
  goalId: number | null;
  goals: readonly DiagnosisGoalImpactItemApiResponse[];
}
