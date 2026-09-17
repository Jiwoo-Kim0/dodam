import { useCallback, useEffect, useMemo, useState } from 'react';

import { ledgerApi } from '@/api/ledgerApi';
import type { DiagnosisIssueApiResponse } from '@/types/discoveryApi.types';
import type { TransactionApiResponse } from '@/types/ledgerApi.types';

import {
  categoryMonthlySpending,
  categorySpendingQuery,
  type CategoryMerchantSpending,
  type CategorySpendingQuery,
} from '../categoryMonthlySpending';
import { loadAllPages } from './useDiscoveryData';

type SpendingState = {
  query: CategorySpendingQuery;
  attempt: number;
  status: 'ready' | 'error';
  merchants: CategoryMerchantSpending[];
};

/** 진단이 본 기간의 이 카테고리 결제를 지출처별로 모아 준다. */
export function useCategoryMonthlySpending(
  issue: DiagnosisIssueApiResponse | null,
) {
  const query = useMemo(() => categorySpendingQuery(issue), [issue]);
  const [state, setState] = useState<SpendingState | null>(null);
  const [retryCount, setRetryCount] = useState(0);

  useEffect(() => {
    if (!query) return;
    let active = true;

    void loadAllPages<TransactionApiResponse>((page) =>
      ledgerApi.getTransactions({
        startDate: query.startDate,
        endDate: query.endDate,
        category: query.categoryCode,
        hidden: false,
        page,
      }),
    )
      .then((transactions) => {
        if (!active) return;
        setState({
          query,
          attempt: retryCount,
          status: 'ready',
          merchants: categoryMonthlySpending(transactions, query),
        });
      })
      .catch(() => {
        if (active) {
          setState({ query, attempt: retryCount, status: 'error', merchants: [] });
        }
      });

    return () => {
      active = false;
    };
  }, [query, retryCount]);

  const retry = useCallback(() => setRetryCount((count) => count + 1), []);
  const hasCurrentResult = state?.query === query && state.attempt === retryCount;
  return {
    hasQuery: query !== null,
    isLoading: query !== null && !hasCurrentResult,
    error: hasCurrentResult && state?.status === 'error',
    merchants: hasCurrentResult && state?.status === 'ready' ? state.merchants : [],
    retry,
  };
}
