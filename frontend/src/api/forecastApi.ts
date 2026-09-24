import type { SpendingForecast } from '@/types/forecastApi.types';

import { apiClient } from './apiClient';

export const forecastApi = {
  /**
   * 월별 변동 소비 예측을 조회한다. 서버가 저장해 둔 결과를 돌려주는 것이 아니라 요청할 때
   * 계산하므로 `/latest`가 아니다. 응답에 최근 12개월 실적이 함께 오므로 화면은 한 번만 부르면 된다.
   */
  getSpendingForecast: (horizonMonths?: number) =>
    apiClient.get<SpendingForecast>('/v1/spending-forecasts', {
      query: { horizonMonths },
    }),
};
