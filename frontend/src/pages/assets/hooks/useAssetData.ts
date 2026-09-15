import { useCallback, useEffect, useRef, useState } from 'react';

import { assetApi } from '@/api';
import { fetchAllPages } from '@/api/pagination';
import type { AssetOverview, AssetSection } from '@/types/asset.types';
import { mapOwnedAssetOverview } from '@/utils/assetOverview';
import { toDateKey } from '@/utils/date';

import { mapAssetSections } from '../utils/assetMappers';

interface AssetData {
  overview: AssetOverview | null;
  sections: readonly AssetSection[];
}

const EMPTY_ASSET_DATA: AssetData = {
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

  const loadAssetData = useCallback(async (refreshing = false) => {
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
      const yearMonth = schedulePeriod.startDate.slice(0, 7);
      const [
        accounts,
        accountSummary,
        cards,
        cardPaymentSchedules,
        loans,
        loanSummary,
        depositSavings,
        depositSavingSummary,
        installments,
        installmentSchedule,
      ] = await Promise.all([
        assetApi.getAccounts(),
        assetApi.getAccountSummary(),
        assetApi.getCards(),
        assetApi.getCardPaymentSchedules(schedulePeriod),
        assetApi.getLoans(),
        assetApi.getLoanSummary(),
        fetchAllPages((page, size) =>
          assetApi.getDepositSavings({ page, size }),
        ),
        assetApi.getDepositSavingSummary(),
        fetchAllPages((page, size) =>
          assetApi.getInstallments({ page, size }),
        ),
        assetApi.getInstallmentSchedule({
          startMonth: yearMonth,
          endMonth: yearMonth,
        }),
      ]);

      if (requestId !== requestIdRef.current) return;

      setData({
        overview: mapOwnedAssetOverview(accountSummary),
        sections: mapAssetSections({
          accounts,
          accountSummary,
          cards,
          cardPaymentSchedules,
          depositSavings,
          depositSavingSummary,
          installments,
          installmentSchedule,
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
