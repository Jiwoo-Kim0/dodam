/** 실제 지출. 거래가 없던 달은 목록에 없다(0이 아니라 결측) */
export interface SpendingForecastHistoryPoint {
  /** `2026-08` */
  month: string;
  amount: number;
}

/**
 * 한 달의 예측. `lowerBound`·`upperBound`는 **정확도를 주장하는 값이 아니라 표시용**이라
 * 화면 문구도 "예상 범위"로 둔다.
 */
export interface SpendingForecastPoint {
  month: string;
  predicted: number;
  lowerBound: number;
  upperBound: number;
  /** 평소 수준에 더해진 추세분. `explanation.level + trendEffect + seasonalEffect === predicted` */
  trendEffect: number;
  /** 계절 계수로 더해진(빠진) 금액. 계절을 쓰지 않았으면 0 */
  seasonalEffect: number;
  /** 이 달의 계절 계수(소수 2자리). 계절을 쓰지 않았으면 null */
  seasonalFactor: number | null;
  /** 1년 전 같은 달 실적. 그 달에 거래가 없었으면 null */
  lastYearAmount: number | null;
}

/** 예측 전체에 공통인 근거. 예측이 없으면 응답에서 null이다 */
export interface SpendingForecastExplanation {
  /** 계절을 뺀 최근 월 소비 수준 */
  level: number;
  /** 계절을 뺀 축의 월당 기울기 */
  monthlyTrend: number;
  /** 이력이 12개월 미만이면 false */
  seasonalityApplied: boolean;
}

export interface SpendingForecast {
  /** 기준 달. 예측은 그다음 달부터 시작한다 */
  baseMonth: string;
  horizonMonths: number;
  /** 축약 경로를 탔을 때만 값이 있다. 이력이 짧으면 `forecasts`가 비어 있다 */
  fallbackReason: string | null;
  explanation: SpendingForecastExplanation | null;
  /** 최근 12개월 실적. 예측선과 이어 그린다 */
  history: SpendingForecastHistoryPoint[];
  forecasts: SpendingForecastPoint[];
}
