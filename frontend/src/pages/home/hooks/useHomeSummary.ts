import { useCallback, useEffect, useRef, useState } from 'react';

import { assetApi, ledgerApi } from '@/api';
import type { AssetOverview } from '@/types/asset.types';
import { mapAssetOverview } from '@/utils/assetOverview';

interface HomeSummaryData {
  monthLabel: string;
  monthlyExpense: number | null;
  assetOverview: AssetOverview | null;
}

function getLatestCompletedMonth() {
  const today = new Date();
  const completedMonth = new Date(today.getFullYear(), today.getMonth() - 1, 1);
  const month = completedMonth.getMonth() + 1;

  return {
    monthLabel: `${month}월`,
    yearMonth: `${completedMonth.getFullYear()}-${String(month).padStart(2, '0')}`,
  };
}

export function useHomeSummary() {
  const latestCompletedMonth = getLatestCompletedMonth();
  const [data, setData] = useState<HomeSummaryData>({
    monthLabel: latestCompletedMonth.monthLabel,
    monthlyExpense: null,
    assetOverview: null,
  });
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const requestIdRef = useRef(0);

  const loadSummary = useCallback(async (refreshing = false) => {
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;

    if (refreshing) {
      setIsRefreshing(true);
    } else {
      setIsLoading(true);
    }
    setError(null);

    try {
      const month = getLatestCompletedMonth();
      const [monthlySummary, accountSummary, loanSummary] = await Promise.all([
        ledgerApi.getMonthlySummary(month.yearMonth),
        assetApi.getAccountSummary(),
        assetApi.getLoanSummary(),
      ]);

      if (requestId !== requestIdRef.current) return;

      setData({
        monthLabel: month.monthLabel,
        monthlyExpense: monthlySummary.totalSpending,
        assetOverview: mapAssetOverview(accountSummary, loanSummary),
      });
    } catch (loadError) {
      if (requestId !== requestIdRef.current) return;

      setError(
        loadError instanceof Error
          ? loadError
          : new Error('홈 요약을 불러오지 못했어요.'),
      );
    } finally {
      if (requestId === requestIdRef.current) {
        setIsLoading(false);
        setIsRefreshing(false);
      }
    }
  }, []);

  useEffect(() => {
    const loadTimeout = setTimeout(() => {
      void loadSummary();
    }, 0);

    return () => {
      clearTimeout(loadTimeout);
      requestIdRef.current += 1;
    };
  }, [loadSummary]);

  const refetch = useCallback(() => loadSummary(true), [loadSummary]);

  return {
    ...data,
    error,
    isLoading,
    isRefreshing,
    refetch,
  };
}
