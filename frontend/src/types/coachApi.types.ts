export type CoachTopic =
  | 'FINANCIAL_SUMMARY'
  | 'INEFFICIENCY_EXPLANATION'
  | 'RECOMMENDATION_EXPLANATION';

export type CoachSource = 'AI' | 'TEMPLATE';

export type CoachDegradedReason =
  | 'AI_DISABLED'
  | 'AI_UNAVAILABLE'
  | 'AI_EMPTY_RESPONSE'
  | 'AI_UNVERIFIED_NUMBER'
  | 'RATE_LIMITED'
  | 'SENSITIVE_DATA_BLOCKED';

export interface CoachFact {
  label: string;
  value: string;
}

export interface CoachFactSection {
  title: string;
  facts: CoachFact[];
}

export interface CoachFacts {
  topic: CoachTopic;
  subject: string;
  sections: CoachFactSection[];
  notes: string[];
}

export interface CoachMessageResponse {
  topic: CoachTopic;
  source: CoachSource;
  degradedReason: CoachDegradedReason | null;
  model: string | null;
  generatedAt: string;
  subject: string;
  message: string;
  highlights: string[];
  facts: CoachFacts;
  disclaimer: string;
}

export interface FinancialSummaryRequest {
  diagnosisId?: number;
}

export interface InefficiencyExplanationRequest {
  diagnosisId: number;
  issueId: number;
}

export interface RecommendationExplanationRequest {
  recommendationId: number;
}
