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
}

export interface SpendingForecast {
  /** 기준 달. 예측은 그다음 달부터 시작한다 */
  baseMonth: string;
  horizonMonths: number;
  /** 축약 경로를 탔을 때만 값이 있다. 이력이 짧으면 `forecasts`가 비어 있다 */
  fallbackReason: string | null;
  /** 최근 12개월 실적. 예측선과 이어 그린다 */
  history: SpendingForecastHistoryPoint[];
  forecasts: SpendingForecastPoint[];
}
