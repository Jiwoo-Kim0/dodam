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
  const [error, setError] = useState<Error | null>(null);
  const requestIdRef = useRef(0);

  const load = useCallback(async () => {
    const requestId = ++requestIdRef.current;
    setIsLoading(true);
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
      if (requestId === requestIdRef.current) setIsLoading(false);
    }
  }, []);

  return {
    error,
    isLoading,
    peerComparison,
    reload: load,
    cancel: useCallback(() => {
      requestIdRef.current += 1;
    }, []),
  };
}
