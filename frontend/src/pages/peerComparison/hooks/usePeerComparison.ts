import { useCallback, useRef, useState } from 'react';

import { isApiError, patternApi } from '@/api';
import type { ReportPeerComparison } from '@/types/report.types';

import {
  PEER_COMPARISON_NOT_ANALYZED,
  toPeerComparison,
} from '../../report/peerComparisonModel';

/**
 * 또래 비교 상세 데이터를 조회한다. 리포트 화면이 이미 `POST /patterns/analyze`로 분석을 만들어 두므로
 * 여기서는 조회만 한다.
 */
export function usePeerComparison() {
  const [peerComparison, setPeerComparison] = useState<ReportPeerComparison | null>(null);
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
      const response = await patternApi.getPeerComparison();
      if (requestId === requestIdRef.current) setPeerComparison(toPeerComparison(response));
    } catch (loadError) {
      if (requestId !== requestIdRef.current) return;

      if (isApiError(loadError) && loadError.status === 404) {
        setPeerComparison(PEER_COMPARISON_NOT_ANALYZED);
      } else {
        setPeerComparison(null);
        setError(
          loadError instanceof Error ? loadError : new Error('또래 비교를 불러오지 못했어요.'),
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
    error,
    isLoading,
    isRefreshing,
    peerComparison,
    refetch: useCallback(() => load(true), [load]),
    reload: load,
    cancel: useCallback(() => {
      requestIdRef.current += 1;
    }, []),
  };
}
