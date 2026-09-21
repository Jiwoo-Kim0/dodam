export type CoachTopic =
  | 'FINANCIAL_SUMMARY'
  | 'INEFFICIENCY_EXPLANATION'
  | 'RECOMMENDATION_EXPLANATION'
  | 'CHAT';

export type CoachSource = 'AI' | 'TEMPLATE';

export type CoachDegradedReason =
  | 'AI_DISABLED'
  | 'AI_UNAVAILABLE'
  | 'AI_EMPTY_RESPONSE'
  | 'AI_UNVERIFIED_NUMBER'
  | 'RATE_LIMITED'
  | 'SENSITIVE_DATA_BLOCKED'
  // 640 채팅에서만 나온다.
  | 'TOOL_LOOP_EXCEEDED'
  | 'SESSION_TURN_LIMIT';

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

/**
 * 토리에게 묻기 시연 칩(§13-2-2). 서버가 LLM 없이 도구 호출로 바로 매핑하므로
 * `chipId`만 보내면 되고 `message`는 생략한다.
 */
export const COACH_CHAT_CHIPS = [
  { id: 'CHIP_SPENDING_CAFE_50K', label: '카페를 월 5만원 줄이면?' },
  { id: 'CHIP_LOAN_PREPAY_1M', label: '대출 100만원 더 갚으면?' },
  { id: 'CHIP_COMBINED', label: '둘 다 하면?' },
] as const;

export type CoachChatChipId = (typeof COACH_CHAT_CHIPS)[number]['id'];

/** 발견 상세에서 열었을 때 넘기는 화면 맥락. 둘 중 하나만 보내면 400이다. */
export interface CoachChatContext {
  diagnosisId: number;
  issueId: number;
}

/**
 * `message`와 `chipId` 중 하나는 있어야 한다(둘 다 없으면 400).
 * `conversationId`를 그대로 돌려보내면 대화가 이어진다 — 서버가 만들고 30분·10턴 제한이 있다.
 */
export interface CoachChatRequest {
  message?: string;
  chipId?: CoachChatChipId;
  context?: CoachChatContext;
  conversationId?: string;
}

/** 서버가 실행한 도구 1건. `error`가 있으면 그 문장을 그대로 보여 준다. */
export interface CoachToolResult {
  tool: string;
  /** 시뮬레이션을 만든 도구만 채워진다. 있으면 상세 조회로 이어 갈 수 있다. */
  simulationId: number | null;
  created: boolean;
  /** 되묻기·검증 실패 사유. 예: "카페 월평균 소비(115,000원)보다 큰 금액이에요." */
  error: string | null;
}

/**
 * `source`가 `TEMPLATE`이어도 200이고 `facts`·`toolResults`는 그대로 온다.
 * 결과 카드는 항상 그린다.
 */
export interface CoachChatResponse {
  topic: 'CHAT';
  source: CoachSource;
  degradedReason: CoachDegradedReason | null;
  model: string | null;
  generatedAt: string;
  conversationId: string;
  message: string;
  facts: CoachFacts;
  toolResults: CoachToolResult[];
  /** 자유 입력 경로로 보내는 문장이다. `chipId`가 아니다. */
  suggestedQuestions: string[];
  disclaimer: string;
}
