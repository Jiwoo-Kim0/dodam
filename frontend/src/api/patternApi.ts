import type {
  AnalyzePatternApiResponse,
  PeerComparisonApiResponse,
  PeerDimension,
} from '@/types/patternApi.types';

import { apiClient } from './apiClient';

export const patternApi = {
  /**
   * 패턴 분석을 실행한다. 오늘 완료된 결과가 있으면 서버가 그대로 재사용하므로(`cached: true`)
   * 화면 진입 때마다 호출해도 실제 계산은 하루 한 번만 일어난다.
   */
  analyze: () => apiClient.post<AnalyzePatternApiResponse>('/v1/patterns/analyze'),

  /**
   * 동년배 비교를 조회한다. 완료된 분석이 없으면 404다. 인구통계 누락·표본 부족처럼 값을 줄 수
   * 없는 사유는 200 응답의 `unavailableReason`으로 내려온다.
   */
  getPeerComparison: (dimension?: PeerDimension) =>
    apiClient.get<PeerComparisonApiResponse>('/v1/patterns/me/peer-comparison', {
      query: { dimension },
    }),
};
