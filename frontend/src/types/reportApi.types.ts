/** 월간 리포트 API(`GET /v1/reports/monthly`) 응답 타입이다. */

/** `READY`가 아니면 수치 대신 안내를 보여 준다. 정상이면 `unavailableReason`이 없다. */
export type ReportDataStatus = 'READY' | 'NO_TRANSACTIONS' | 'INSUFFICIENT_DATA';

export type EfficiencyDimensionKey =
  | 'SPENDING'
  | 'SAVING'
  | 'FIXED_EXPENSE'
  | 'CREDIT'
  | 'CASHFLOW'
  | 'INEFFICIENCY';

/** `GOOD` ≥ 70, `NORMAL` 40~69, `CHECK` < 40, `UNAVAILABLE`은 계산 불가다. */
export type EfficiencyDimensionStatus = 'GOOD' | 'NORMAL' | 'CHECK' | 'UNAVAILABLE';

export type EfficiencyValueType = 'RATIO' | 'COUNT' | 'GRADE';

export interface EfficiencyDimensionApiResponse {
  key: EfficiencyDimensionKey;
  label: string;
  /** 0~100. 계산할 수 없으면 null이며 0점과 구분해야 한다. */
  score: number | null;
  maxScore: number;
  status: EfficiencyDimensionStatus;
  /** 숫자 근거가 담긴 서버 문장이다. 계산 불가면 그 이유 문장이 온다. */
  description: string;
  /** 비율 항목은 0~1, 건수 항목은 건수. 등급 항목은 값 대신 `grade`가 온다. */
  value?: number;
  valueType?: EfficiencyValueType;
  /** 신용등급 A~E. `CREDIT` 항목에만 있다. */
  grade?: string;
  /** INCOME_MISSING, NO_SPENDING, CREDIT_RATING_MISSING, FORECAST_NOT_FOUND, DIAGNOSIS_NOT_FOUND */
  unavailableReason?: string;
}

/**
 * 금융 효율 점수. 전체 점수는 계산된 항목의 단순 평균이며, 계산된 항목이 너무 적으면 전체도 null이고
 * `unavailableReason`이 `INSUFFICIENT_DIMENSIONS`다. 계산 불가 항목을 0점으로 넣어 평균을 낮추지 않는다.
 */
export interface EfficiencyScoreApiResponse {
  score: number | null;
  maxScore: number;
  /** 전월 대비 점수 차(부호 유지). 비교할 수 없으면 null이다. */
  previousMonthDifference: number | null;
  summary: string;
  calculationVersion: string;
  availableDimensionCount: number;
  unavailableReason?: string;
  /** SPENDING, SAVING, FIXED_EXPENSE, CREDIT, CASHFLOW, INEFFICIENCY 순서로 고정이다. */
  dimensions: EfficiencyDimensionApiResponse[];
}

export interface MonthAmountsApiResponse {
  totalIncome: number;
  totalExpense: number;
  totalSpending: number;
  totalSaving: number;
}

export interface MonthlyReportApiResponse {
  reportId: number | null;
  type: string;
  /** yyyy-MM */
  period: string;
  generatedAt: string;
  dataStatus: ReportDataStatus;
  totalIncome: number | null;
  /** 저축성 지출을 포함한 지출 전체다. */
  totalExpense: number | null;
  /** 소비 정의(저축성 지출 제외)다. 소비 화면·패턴 API와 같은 숫자다. */
  totalSpending: number | null;
  totalSaving: number | null;
  netCashflow: number | null;
  transactionCount: number | null;
  /** 비교할 이전 달이 사용자 데이터 이전이면 null이다. */
  previousMonth: MonthAmountsApiResponse | null;
  changeFromPreviousMonth: MonthAmountsApiResponse | null;
  /** 카드 사용액. `totalSpending`의 부분집합이다. */
  cardSpending: number | null;
  cardSpendingPreviousMonth: number | null;
  /** 전월 대비 증감(부호 유지). 비교 대상이 없으면 null이다. */
  cardSpendingDifferenceFromPreviousMonth: number | null;
  efficiency: EfficiencyScoreApiResponse | null;
  diagnosisId: number | null;
  overallStatus: string | null;
  unavailableReason?: string;
}
