import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { ledgerApi } from '@/api';
import { fetchAllPages } from '@/api/pagination';
import type {
  CreateManualTransactionRequest,
  UpdateManualTransactionRequest,
} from '@/types/ledgerApi.types';
import type {
  LedgerCategory,
  LedgerCategorySummary,
  LedgerDailyTotal,
  LedgerMonthlySummary,
  LedgerTransaction,
} from '@/types/ledger.types';

import {
  mapCategorySpending,
  mapDailySpending,
  mapMonthlySummary,
  mapTransactionCategories,
  mapTransactions,
} from '../utils/ledgerMappers';

interface LedgerData {
  categories: LedgerCategory[];
  hiddenTransactions: LedgerTransaction[];
  transactions: LedgerTransaction[];
  monthlySummary: LedgerMonthlySummary | null;
  dailyTotals: LedgerDailyTotal[];
  previousDailyTotals: LedgerDailyTotal[];
  categorySummaries: LedgerCategorySummary[];
}

const EMPTY_LEDGER_DATA: LedgerData = {
  categories: [],
  hiddenTransactions: [],
  transactions: [],
  monthlySummary: null,
  dailyTotals: [],
  previousDailyTotals: [],
  categorySummaries: [],
};

function padDatePart(value: number) {
  return String(value).padStart(2, '0');
}

export function useLedgerData(currentMonth: Date) {
  const [data, setData] = useState<LedgerData>(EMPTY_LEDGER_DATA);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isLoadingHiddenTransactions, setIsLoadingHiddenTransactions] = useState(false);
  const [hiddenTransactionsError, setHiddenTransactionsError] = useState<Error | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const [mutationError, setMutationError] = useState<Error | null>(null);
  const [isMutating, setIsMutating] = useState(false);
  const requestIdRef = useRef(0);
  const hiddenRequestedMonthRef = useRef<string | null>(null);
  const mutationRequestIdRef = useRef(0);
  const mutationInFlightRef = useRef(false);
  const isMountedRef = useRef(true);

  const dateParams = useMemo(() => {
    const year = currentMonth.getFullYear();
    const month = currentMonth.getMonth() + 1;
    const yearMonth = `${year}-${padDatePart(month)}`;
    const lastDay = new Date(year, month, 0).getDate();
    const today = new Date();
    const endDay =
      year === today.getFullYear() && month === today.getMonth() + 1
        ? today.getDate()
        : lastDay;
    const previousMonthDate = new Date(year, month - 2, 1);
    const previousYear = previousMonthDate.getFullYear();
    const previousMonth = previousMonthDate.getMonth() + 1;
    const previousYearMonth = `${previousYear}-${padDatePart(previousMonth)}`;
    const previousLastDay = new Date(previousYear, previousMonth, 0).getDate();
    const previousEndDay = Math.min(endDay, previousLastDay);

    return {
      yearMonth,
      startDate: `${yearMonth}-01`,
      endDate: `${yearMonth}-${padDatePart(endDay)}`,
      previousStartDate: `${previousYearMonth}-01`,
      previousEndDate: `${previousYearMonth}-${padDatePart(previousEndDay)}`,
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
        const includeHidden = hiddenRequestedMonthRef.current === dateParams.yearMonth;
        const [
          transactionResponses,
          hiddenTransactionResponses,
          transactionCategoryResponses,
          monthlySummaryResponse,
          dailySpendingResponse,
          categorySpendingResponse,
          previousDailySpendingResponse,
        ] = await Promise.all([
          fetchAllPages((page, size) =>
            ledgerApi.getTransactions({
              startDate: dateParams.startDate,
              endDate: dateParams.endDate,
              page,
              size,
            }),
          ),
          includeHidden
            ? fetchAllPages((page, size) =>
                ledgerApi.getTransactions({
                  startDate: dateParams.startDate,
                  endDate: dateParams.endDate,
                  hidden: true,
                  page,
                  size,
                }),
              )
            : Promise.resolve([]),
          ledgerApi.getTransactionCategories(),
          ledgerApi.getMonthlySummary(dateParams.yearMonth),
          ledgerApi.getDailySpending({
            startDate: dateParams.startDate,
            endDate: dateParams.endDate,
          }),
          ledgerApi.getCategorySpending(dateParams.yearMonth),
          ledgerApi
            .getDailySpending({
              startDate: dateParams.previousStartDate,
              endDate: dateParams.previousEndDate,
            })
            .catch(() => null),
        ]);

        if (requestId !== requestIdRef.current) return;

        const incomeByDate = transactionResponses.reduce<Record<string, number>>(
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

        setData((current) => ({
          categories: mapTransactionCategories(transactionCategoryResponses),
          hiddenTransactions: includeHidden
            ? mapTransactions(hiddenTransactionResponses)
            : hiddenRequestedMonthRef.current === dateParams.yearMonth
              ? current.hiddenTransactions
              : [],
          transactions: mapTransactions(transactionResponses),
          monthlySummary: mapMonthlySummary(monthlySummaryResponse, income),
          dailyTotals: mapDailySpending(dailySpendingResponse, incomeByDate),
          previousDailyTotals: previousDailySpendingResponse
            ? mapDailySpending(previousDailySpendingResponse)
            : [],
          categorySummaries: mapCategorySpending(categorySpendingResponse),
        }));
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

  const loadHiddenTransactions = useCallback(async () => {
    if (hiddenRequestedMonthRef.current === dateParams.yearMonth) return;
    const requestId = requestIdRef.current;
    setIsLoadingHiddenTransactions(true);
    setHiddenTransactionsError(null);
    try {
      const hidden = await fetchAllPages((page, size) =>
        ledgerApi.getTransactions({
          startDate: dateParams.startDate,
          endDate: dateParams.endDate,
          hidden: true,
          page,
          size,
        }),
      );
      if (requestId !== requestIdRef.current) return;
      hiddenRequestedMonthRef.current = dateParams.yearMonth;
      setData((current) => ({ ...current, hiddenTransactions: mapTransactions(hidden) }));
    } catch (loadError) {
      if (requestId === requestIdRef.current) {
        setHiddenTransactionsError(loadError instanceof Error ? loadError : new Error('숨긴 거래를 불러오지 못했어요.'));
      }
    } finally {
      if (requestId === requestIdRef.current) setIsLoadingHiddenTransactions(false);
    }
  }, [dateParams]);

  useEffect(() => {
    const loadTimeout = setTimeout(() => {
      void loadLedgerData();
    }, 0);

    return () => {
      clearTimeout(loadTimeout);
      requestIdRef.current += 1;
    };
  }, [loadLedgerData]);

  useEffect(() => {
    isMountedRef.current = true;

    return () => {
      isMountedRef.current = false;
      requestIdRef.current += 1;
      mutationRequestIdRef.current += 1;
      mutationInFlightRef.current = false;
    };
  }, []);

  const refetch = useCallback(
    () => loadLedgerData(true),
    [loadLedgerData],
  );

  const executeMutation = useCallback(
    async <T>(request: () => Promise<T>): Promise<T | null> => {
      if (mutationInFlightRef.current) return null;

      const mutationRequestId = ++mutationRequestIdRef.current;
      mutationInFlightRef.current = true;
      setIsMutating(true);
      setMutationError(null);
      try {
        const response = await request();
        if (
          !isMountedRef.current ||
          mutationRequestId !== mutationRequestIdRef.current
        ) {
          return null;
        }
        await loadLedgerData(true);
        if (
          !isMountedRef.current ||
          mutationRequestId !== mutationRequestIdRef.current
        ) {
          return null;
        }
        return response;
      } catch (mutationFailure) {
        if (
          isMountedRef.current &&
          mutationRequestId === mutationRequestIdRef.current
        ) {
          setMutationError(
            mutationFailure instanceof Error
              ? mutationFailure
              : new Error('거래 내역을 변경하지 못했어요.'),
          );
        }
        return null;
      } finally {
        if (mutationRequestId === mutationRequestIdRef.current) {
          mutationInFlightRef.current = false;
          if (isMountedRef.current) setIsMutating(false);
        }
      }
    },
    [loadLedgerData],
  );

  const updateTransactionCategory = useCallback(
    (transactionId: string, categoryId: number) =>
      executeMutation(() =>
        ledgerApi.updateTransactionCategory(transactionId, categoryId),
      ),
    [executeMutation],
  );

  const updateTransactionMemo = useCallback(
    (transactionId: string, memo: string) =>
      executeMutation(() => ledgerApi.updateTransactionMemo(transactionId, memo)),
    [executeMutation],
  );

  const updateTransactionVisibility = useCallback(
    (transactionId: string, hidden: boolean) =>
      executeMutation(() =>
        ledgerApi.updateTransactionVisibility(transactionId, hidden),
      ),
    [executeMutation],
  );

  const createManualTransaction = useCallback(
    (request: CreateManualTransactionRequest) =>
      executeMutation(() => ledgerApi.createManualTransaction(request)),
    [executeMutation],
  );

  const updateManualTransaction = useCallback(
    (transactionId: string, request: UpdateManualTransactionRequest) =>
      executeMutation(() =>
        ledgerApi.updateManualTransaction(transactionId, request),
      ),
    [executeMutation],
  );

  const deleteManualTransaction = useCallback(
    (transactionId: string) =>
      executeMutation(() => ledgerApi.deleteManualTransaction(transactionId)),
    [executeMutation],
  );

  return {
    ...data,
    isLoading,
    isRefreshing,
    isLoadingHiddenTransactions,
    hiddenTransactionsError,
    error,
    mutationError,
    refetch,
    loadHiddenTransactions,
    isMutating,
    updateTransactionCategory,
    updateTransactionMemo,
    updateTransactionVisibility,
    createManualTransaction,
    updateManualTransaction,
    deleteManualTransaction,
  };
}
