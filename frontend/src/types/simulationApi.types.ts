export type SimulationType =
  | 'SPENDING'
  | 'SAVING'
  | 'SAVING_PRODUCT'
  | 'LOAN_NEW'
  | 'LOAN_PREPAYMENT'
  | 'LOAN_REFINANCE'
  | 'INSTALLMENT'
  | 'REVOLVING'
  | 'CASH_ADVANCE'
  | 'COMBINED';

/** D10에서 화면과 API를 연결하는 시뮬레이션 유형. */
export type BasicSimulationType = Extract<SimulationType, 'SPENDING' | 'SAVING'>;

/** 후속 작업에서 화면을 연결할 유형. 백엔드 실행 API는 이미 제공된다. */
export type PendingSimulationType = Exclude<SimulationType, BasicSimulationType>;

export type SimulationStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface SpendingSimulationRequest {
  /** 생략하면 전체 소비를 기준으로 계산한다. */
  category?: string;
  monthlyReduction: number;
  /** 생략하면 백엔드 기본값인 12개월을 사용한다. */
  durationMonths?: number;
}

export interface SavingSimulationRequest {
  /** 원문 계좌번호는 요청에만 사용되고 응답이나 로그에는 남지 않는다. */
  accountNo?: string;
  /**
   * 새 저축액을 받을 모으기 목표. 추천 프리셋이 원문 계좌번호 없이 목표를 겨냥하는 경로다(619).
   * `accountNo`와 함께 보내면 400이다.
   */
  goalId?: number;
  /** 변경 후 월 저축액의 절대값이며 0원도 허용한다. */
  monthlySavingAmount: number;
  /** 생략하면 백엔드 기본값인 12개월을 사용한다. */
  durationMonths?: number;
}

export interface SpendingSimulationRequestSnapshot {
  category: string | null;
  monthlyReduction: number;
  durationMonths: number;
}

export interface SavingSimulationRequestSnapshot {
  /** 서버가 확인한 내부 계좌 식별자. 원문 계좌번호는 응답하지 않는다. */
  accountId: number | null;
  goalId: number | null;
  monthlySavingAmount: number;
  durationMonths: number;
}

export type BasicSimulationRequestSnapshot =
  | SpendingSimulationRequestSnapshot
  | SavingSimulationRequestSnapshot;

export interface SimulationGoalForecastApiResponse {
  goalId: number;
  name: string;
  remaining: number;
  monthlySaving: number;
  expectedAchievementDate: string | null;
}

export interface SimulationSnapshotApiResponse {
  baseDate: string;
  monthlyIncome: number | null;
  monthlySpending: number;
  monthlySaving: number;
  monthlyAvailableFunds: number | null;
  goals: readonly SimulationGoalForecastApiResponse[];
  /** 기준선 계산에 필요한 데이터가 없을 때 before 스냅샷에 포함된다. */
  coverage?: readonly string[];
  /** 계산에 적용한 가정 코드이며 after 스냅샷에 포함된다. */
  assumptions?: readonly string[];
}

export type SimulationComparisonValue = number | string | null;

export interface SimulationComparisonItemApiResponse {
  key: string;
  label: string;
  before: SimulationComparisonValue;
  after: SimulationComparisonValue;
  /** 금액 차이 또는 날짜 차이(일). 계산할 수 없으면 null이다. */
  delta: number | null;
}

export interface SimulationComparisonApiResponse {
  items: readonly SimulationComparisonItemApiResponse[];
  assets: readonly SimulationComparisonItemApiResponse[];
  debts: readonly SimulationComparisonItemApiResponse[];
  costs: readonly SimulationComparisonItemApiResponse[];
}

export interface SimulationCashflowMonthApiResponse {
  yearMonth: string;
  delta: number;
  cumulative: number;
}

export interface SimulationCashflowCurrentMonthApiResponse {
  yearMonth: string;
  delta: number;
}

export interface SimulationCashflowApiResponse {
  basis: 'MONTHLY_AVAILABLE_FUNDS' | 'SCENARIO_NET_CASH_CHANGE';
  before: number | null;
  after: number | null;
  months: readonly SimulationCashflowMonthApiResponse[];
  /** 후속 시뮬레이션 유형에서만 내려오는 확장 필드다. */
  currentMonth?: SimulationCashflowCurrentMonthApiResponse;
  truncated?: boolean;
  coveredThrough?: string;
  fullTermEndDate?: string;
}

export interface SimulationGoalImpactApiResponse {
  goalId: number;
  beforeForecastDate: string | null;
  afterForecastDate: string | null;
  /** after - before. 음수면 목표 달성일이 앞당겨진다. */
  differenceDays: number | null;
  status?: 'COMPUTED' | 'NOT_COMPUTABLE';
  reasons?: readonly string[];
}

export type SimulationJsonValue =
  | string
  | number
  | boolean
  | null
  | readonly SimulationJsonValue[]
  | { readonly [key: string]: SimulationJsonValue };

export type SimulationJsonObject = Readonly<Record<string, SimulationJsonValue>>;

export interface SimulationScenarioApiResponse<
  TParameters = SimulationJsonObject,
> {
  sequenceNo: number;
  scenarioType: SimulationType;
  parameters: TParameters;
}

export interface SimulationSummaryApiResponse {
  simulationId: number;
  type: SimulationType;
  title: string;
  summary: string;
  status: SimulationStatus;
  createdAt: string;
  completedAt: string | null;
}

export interface SimulationApiResponse<
  TRequest = SimulationJsonObject,
  TScenarioParameters = SimulationJsonObject,
> extends SimulationSummaryApiResponse {
  request: TRequest;
  scenarios: readonly SimulationScenarioApiResponse<TScenarioParameters>[];
  before: SimulationSnapshotApiResponse;
  after: SimulationSnapshotApiResponse;
  comparison: SimulationComparisonApiResponse;
  cashflow: SimulationCashflowApiResponse;
  goalImpacts: readonly SimulationGoalImpactApiResponse[];
}

export type BasicSimulationApiResponse = SimulationApiResponse<
  BasicSimulationRequestSnapshot,
  BasicSimulationRequestSnapshot
>;

export interface SimulationRunApiResponse {
  simulation: BasicSimulationApiResponse;
  /** false면 같은 날 실행한 동일 요청의 기존 결과를 재사용한 것이다. */
  created: boolean;
}

export interface GetSimulationsParams {
  type?: SimulationType;
  page?: number;
  size?: number;
}
