import { useCallback, useEffect, useState } from 'react';

import { ApiError, financeDataApi, financeErrorCodes, isApiError } from '@/api';
import type { FinanceSync } from '@/types/finance.types';
import {
  FINANCE_SYNC_TIMEOUT_CODE,
  getFinanceErrorMessage,
} from '@/utils/financeErrorMessage';

/** 서버가 동기화를 동기 실행하므로 진행률은 응답 전까지 알 수 없다. 화면은 세 상태만 구분한다. */
export type AssetSyncPhase = 'syncing' | 'completed' | 'failed';

const STATUS_POLL_INTERVAL_MS = 2000;
/** 2초 간격 90회 = 최대 3분. 서버가 만료 처리하는 10분보다 짧게 잡아 화면이 먼저 실패를 알린다. */
const STATUS_POLL_LIMIT = 90;

function delay(durationMs: number) {
  return new Promise<void>((resolve) => setTimeout(resolve, durationMs));
}

/**
 * 다른 요청이 이미 동기화를 돌리고 있으면(409) 새로 시작하지 않고 끝날 때까지 상태만 확인한다.
 */
async function waitForRunningSync(): Promise<FinanceSync> {
  for (let attempt = 0; attempt < STATUS_POLL_LIMIT; attempt += 1) {
    await delay(STATUS_POLL_INTERVAL_MS);

    const { inProgress, sync } = await financeDataApi.getSyncStatus();
    if (!inProgress && sync) return sync;
  }

  throw new ApiError({
    status: 0,
    code: FINANCE_SYNC_TIMEOUT_CODE,
    message: '동기화가 끝나기를 기다리는 동안 제한 시간을 넘겼습니다.',
  });
}

async function runSync(): Promise<FinanceSync> {
  try {
    return await financeDataApi.sync('ALL');
  } catch (error) {
    if (isApiError(error) && error.code === financeErrorCodes.syncInProgress) {
      return waitForRunningSync();
    }

    throw error;
  }
}

/**
 * 금융 데이터 갱신 실행과 그 결과 상태.
 * 실패 문구는 오류 코드로만 고르고 서버가 준 `errorMessage` 원문은 화면에 쓰지 않는다.
 */
export function useAssetSync() {
  const [phase, setPhase] = useState<AssetSyncPhase>('syncing');
  const [failureMessage, setFailureMessage] = useState('');
  const [requiresReconnect, setRequiresReconnect] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let isActive = true;

    const start = async () => {
      try {
        const result = await runSync();
        if (!isActive) return;

        if (result.status === 'COMPLETED') {
          setPhase('completed');
          return;
        }

        setPhase('failed');
        setFailureMessage('금융 정보를 모두 가져오지 못했어요. 다시 시도해주세요.');
      } catch (error) {
        if (!isActive) return;

        setPhase('failed');
        setFailureMessage(getFinanceErrorMessage(error));
        // 연결이 끊긴 상태에서는 재시도해도 같은 오류가 나므로 연결 화면으로 돌려보낸다.
        setRequiresReconnect(
          isApiError(error) && error.code === financeErrorCodes.notConnected,
        );
      }
    };

    void start();

    return () => {
      isActive = false;
    };
  }, [attempt]);

  const retry = useCallback(() => {
    setPhase('syncing');
    setFailureMessage('');
    setRequiresReconnect(false);
    setAttempt((count) => count + 1);
  }, []);

  return { phase, failureMessage, requiresReconnect, retry };
}
