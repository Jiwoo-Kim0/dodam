import { useCallback, useRef, useState } from 'react';

import { assetApi, safeboxApi } from '@/api';
import type { SafeboxAccountResponse } from '@/api/safeboxApi';
import { fetchAllPages } from '@/api/pagination';
import type { AssetAnalysis, AssetOverview, AssetSection } from '@/types/asset.types';
import type { AccountApiResponse } from '@/types/assetApi.types';
import { mapOwnedAssetOverview } from '@/utils/assetOverview';
import { toDateKey } from '@/utils/date';

import { mapAssetSections } from '../utils/assetMappers';

interface AssetData {
  analysis: AssetAnalysis | null;
  hasAssets: boolean;
  overview: AssetOverview | null;
  sections: readonly AssetSection[];
  /** 계좌 간 이체에서 고를 수 있는 입출금 계좌(해지 제외, 도담 계좌 포함). */
  transferAccounts: readonly AccountApiResponse[];
  safeboxAccount: SafeboxAccountResponse | null;
}

const EMPTY_ASSET_DATA: AssetData = {
  analysis: null,
  hasAssets: false,
  overview: null,
  sections: [],
  transferAccounts: [],
  safeboxAccount: null,
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
        safeboxAccount,
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
        safeboxApi.getAccountOrNull(),
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
        overview: mapOwnedAssetOverview(
          accountSummary,
          Math.abs(loanSummary.totalBalance),
        ),
        sections: mapAssetSections({
          accounts,
          accountSummary,
          cards,
          cardPaymentSchedules,
          depositSavings,
          depositSavingSummary,
          loans,
          loanSummary,
          safeboxAccount,
        }),
        transferAccounts: accounts.filter(
          ({ accountType, status }) => accountType === 'DEMAND_DEPOSIT' && status !== 'CLOSED',
        ),
        safeboxAccount,
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

  const refetch = useCallback(() => loadAssetData(true), [loadAssetData]);

  return {
    ...data,
    error,
    isLoading,
    isRefreshing,
    refetch,
    reload: useCallback(() => loadAssetData(), [loadAssetData]),
    /** 화면을 벗어날 때 부른다. 돌아온 뒤 다시 조회할 수 있도록 진행 중 표시도 함께 푼다. */
    cancel: useCallback(() => {
      requestIdRef.current += 1;
      loadInFlightRef.current = false;
    }, []),
  };
}
