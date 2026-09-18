import { useCallback, useRef, useState } from 'react';

import { isApiError, reportApi } from '@/api';
import type { ReportEfficiencyScore } from '@/types/report.types';

import {
  EFFICIENCY_SCORE_UNAVAILABLE,
  toEfficiencyScore,
} from '../../report/efficiencyScoreModel';

function monthAt(offset: number) {
  const today = new Date();
  const date = new Date(today.getFullYear(), today.getMonth() + offset, 1);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

/** 리포트와 같은 기준 달(직전 완료 월)의 금융 효율 점수를 조회한다. */
export function useEfficiencyScore() {
  const [efficiencyScore, setEfficiencyScore] = useState<ReportEfficiencyScore | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const requestIdRef = useRef(0);

  // 당겨서 새로고침일 때는 전체 로딩 화면 대신 스크롤 상단 인디케이터만 돈다.
  const load = useCallback(async (refreshing = false) => {
    const requestId = ++requestIdRef.current;
    if (refreshing) {
      setIsRefreshing(true);
    } else {
      setIsLoading(true);
    }
    setError(null);

    try {
      const report = await reportApi.getMonthly(monthAt(-1));
      if (requestId === requestIdRef.current) {
        setEfficiencyScore(toEfficiencyScore(report.efficiency));
      }
    } catch (loadError) {
      if (requestId !== requestIdRef.current) return;

      if (isApiError(loadError) && loadError.status === 404) {
        setEfficiencyScore(EFFICIENCY_SCORE_UNAVAILABLE);
      } else {
        setEfficiencyScore(null);
        setError(
          loadError instanceof Error ? loadError : new Error('금융 효율 점수를 불러오지 못했어요.'),
        );
      }
    } finally {
      if (requestId === requestIdRef.current) {
        setIsLoading(false);
        setIsRefreshing(false);
      }
    }
  }, []);

  return {
    efficiencyScore,
    error,
    isLoading,
    isRefreshing,
    refetch: useCallback(() => load(true), [load]),
    reload: load,
    cancel: useCallback(() => {
      requestIdRef.current += 1;
    }, []),
  };
}
