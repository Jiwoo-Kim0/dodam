import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { ledgerApi } from '@/api';
import type {
  LedgerCategorySummary,
  LedgerDailyTotal,
  LedgerMonthlySummary,
  LedgerTransaction,
} from '@/types/ledger.types';

import {
  mapCategorySpending,
  mapDailySpending,
  mapMonthlySummary,
  mapTransactions,
} from '../utils/ledgerMappers';

interface LedgerData {
  transactions: LedgerTransaction[];
  monthlySummary: LedgerMonthlySummary | null;
  dailyTotals: LedgerDailyTotal[];
  categorySummaries: LedgerCategorySummary[];
}

const EMPTY_LEDGER_DATA: LedgerData = {
  transactions: [],
  monthlySummary: null,
  dailyTotals: [],
  categorySummaries: [],
};

function padDatePart(value: number) {
  return String(value).padStart(2, '0');
}

export function useLedgerData(currentMonth: Date) {
  const [data, setData] = useState<LedgerData>(EMPTY_LEDGER_DATA);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const requestIdRef = useRef(0);

  const dateParams = useMemo(() => {
    const year = currentMonth.getFullYear();
    const month = currentMonth.getMonth() + 1;
    const yearMonth = `${year}-${padDatePart(month)}`;
    const lastDay = new Date(year, month, 0).getDate();

    return {
      yearMonth,
      startDate: `${yearMonth}-01`,
      endDate: `${yearMonth}-${padDatePart(lastDay)}`,
    };
  }, [currentMonth]);

  const loadLedgerData = useCallback(
    async (refreshing = false) => {
      const requestId = requestIdRef.current + 1;
      requestIdRef.current = requestId;

      if (refreshing) {
        setIsRefreshing(true);
      } else {
        setIsLoading(true);
        setData(EMPTY_LEDGER_DATA);
      }

      setError(null);

      try {
        const [
          transactionPage,
          monthlySummaryResponse,
          dailySpendingResponse,
          categorySpendingResponse,
        ] = await Promise.all([
          ledgerApi.getTransactions({
            startDate: dateParams.startDate,
            endDate: dateParams.endDate,
            page: 0,
            size: 100,
          }),
          ledgerApi.getMonthlySummary(dateParams.yearMonth),
          ledgerApi.getDailySpending({
            startDate: dateParams.startDate,
            endDate: dateParams.endDate,
          }),
          ledgerApi.getCategorySpending(dateParams.yearMonth),
        ]);

        if (requestId !== requestIdRef.current) return;

        const incomeByDate = transactionPage.content.reduce<Record<string, number>>(
          (result, transaction) => {
            if (transaction.transactionType === 'INCOME') {
              result[transaction.transactionDate] =
                (result[transaction.transactionDate] ?? 0) + transaction.amount;
            }

            return result;
          },
          {},
        );

        const income = Object.values(incomeByDate).reduce(
          (total, amount) => total + amount,
          0,
        );

        setData({
          transactions: mapTransactions(transactionPage.content),
          monthlySummary: mapMonthlySummary(monthlySummaryResponse, income),
          dailyTotals: mapDailySpending(dailySpendingResponse, incomeByDate),
          categorySummaries: mapCategorySpending(categorySpendingResponse),
        });
      } catch (loadError) {
        if (requestId !== requestIdRef.current) return;

        setError(
          loadError instanceof Error
            ? loadError
            : new Error('가계부 데이터를 불러오지 못했어요.'),
        );
      } finally {
        if (requestId === requestIdRef.current) {
          setIsLoading(false);
          setIsRefreshing(false);
        }
      }
    },
    [dateParams],
  );

  useEffect(() => {
    const loadTimeout = setTimeout(() => {
      void loadLedgerData();
    }, 0);

    return () => {
      clearTimeout(loadTimeout);
      requestIdRef.current += 1;
    };
  }, [loadLedgerData]);

  const refetch = useCallback(
    () => loadLedgerData(true),
    [loadLedgerData],
  );

  const updateTransaction = useCallback(
    (transactionId: string, patch: Partial<LedgerTransaction>) => {
      setData((current) => ({
        ...current,
        transactions: current.transactions.map((transaction) =>
          transaction.id === transactionId
            ? { ...transaction, ...patch }
            : transaction,
        ),
      }));
    },
    [],
  );

  const prependTransaction = useCallback((transaction: LedgerTransaction) => {
    setData((current) => ({
      ...current,
      transactions: [transaction, ...current.transactions],
    }));
  }, []);

  const removeTransaction = useCallback((transactionId: string) => {
    setData((current) => ({
      ...current,
      transactions: current.transactions.filter(
        (transaction) => transaction.id !== transactionId,
      ),
    }));
  }, []);

  return {
    ...data,
    isLoading,
    isRefreshing,
    error,
    refetch,
    updateTransaction,
    prependTransaction,
    removeTransaction,
  };
}
