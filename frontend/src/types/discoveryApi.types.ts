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
}

export interface RecommendationGenerationApiResponse {
  diagnosisId: number;
  created: number;
  reused: number;
}
