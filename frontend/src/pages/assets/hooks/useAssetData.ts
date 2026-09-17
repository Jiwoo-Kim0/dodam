import { useCallback, useEffect, useRef, useState } from 'react';

import { assetApi } from '@/api';
import { fetchAllPages } from '@/api/pagination';
import type { AssetAnalysis, AssetOverview, AssetSection } from '@/types/asset.types';
import { mapOwnedAssetOverview } from '@/utils/assetOverview';
import { toDateKey } from '@/utils/date';

import { mapAssetSections } from '../utils/assetMappers';

interface AssetData {
  analysis: AssetAnalysis | null;
  hasAssets: boolean;
  overview: AssetOverview | null;
  sections: readonly AssetSection[];
}

const EMPTY_ASSET_DATA: AssetData = {
  analysis: null,
  hasAssets: false,
  overview: null,
  sections: [],
};

function getCurrentMonthSchedulePeriod() {
  const today = new Date();
  const startOfMonth = new Date(today.getFullYear(), today.getMonth(), 1);
  const endOfMonth = new Date(today.getFullYear(), today.getMonth() + 1, 0);

  return {
    startDate: toDateKey(startOfMonth),
    endDate: toDateKey(endOfMonth),
  };
}

export function useAssetData() {
  const [data, setData] = useState<AssetData>(EMPTY_ASSET_DATA);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const requestIdRef = useRef(0);
  const loadInFlightRef = useRef(false);

  const loadAssetData = useCallback(async (refreshing = false) => {
    if (loadInFlightRef.current) return;

    loadInFlightRef.current = true;
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;

    if (refreshing) {
      setIsRefreshing(true);
    } else {
      setIsLoading(true);
    }

    setError(null);

    try {
      const schedulePeriod = getCurrentMonthSchedulePeriod();
      const [
        accounts,
        accountSummary,
        accountBalanceSummary,
        inactiveAccounts,
        cards,
        cardPaymentSchedules,
        loans,
        loanSummary,
        depositSavings,
        depositSavingSummary,
      ] = await Promise.all([
        assetApi.getAccounts(),
        assetApi.getAccountSummary(),
        assetApi.getAccountBalanceSummary(),
        assetApi.getInactiveAccounts(),
        assetApi.getCards(),
        assetApi.getCardPaymentSchedules(schedulePeriod),
        assetApi.getLoans(),
        assetApi.getLoanSummary(),
        fetchAllPages((page, size) =>
          assetApi.getDepositSavings({ page, size }),
        ),
        assetApi.getDepositSavingSummary(),
      ]);

      if (requestId !== requestIdRef.current) return;

      setData({
        analysis: {
          cashTotal: accountBalanceSummary.totalCash,
          cashAccountCount: accountBalanceSummary.accountCount,
          inactiveAccountCount: inactiveAccounts.count,
          inactiveBalance: inactiveAccounts.totalBalance,
          inactiveMonths: inactiveAccounts.criteria.inactiveMonths,
        },
        hasAssets:
          accounts.length > 0 ||
          cards.length > 0 ||
          loans.length > 0 ||
          depositSavings.length > 0,
        overview: mapOwnedAssetOverview(accountSummary),
        sections: mapAssetSections({
          accounts,
          accountSummary,
          cards,
          cardPaymentSchedules,
          depositSavings,
          depositSavingSummary,
          loans,
          loanSummary,
        }),
      });
    } catch (loadError) {
      if (requestId !== requestIdRef.current) return;

      setError(
        loadError instanceof Error
          ? loadError
          : new Error('자산 데이터를 불러오지 못했어요.'),
      );
    } finally {
      if (requestId === requestIdRef.current) {
        loadInFlightRef.current = false;
        setIsLoading(false);
        setIsRefreshing(false);
      }
    }
  }, []);

  useEffect(() => {
    const loadTimeout = setTimeout(() => {
      void loadAssetData();
    }, 0);

    return () => {
      clearTimeout(loadTimeout);
      requestIdRef.current += 1;
      loadInFlightRef.current = false;
    };
  }, [loadAssetData]);

  const refetch = useCallback(() => loadAssetData(true), [loadAssetData]);

  return {
    ...data,
    error,
    isLoading,
    isRefreshing,
    refetch,
  };
}
