import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { appDialog } from '@/components/appDialog/appDialog';
import { AppHeader } from '@/components/appHeader/AppHeader';
import { ScreenState } from '@/components/screenState/ScreenState';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { EMPTY_LEDGER_DRAFT } from '@/mocks/ledger';
import type {
  LedgerTransaction,
  LedgerTransactionDraft,
} from '@/types/ledger.types';
import { toDateKey, toTimeKeyFromParts } from '@/utils/date';

import { styles } from './LedgerScreen.styles';
import { CategorySelection } from './components/CategorySelection';
import { LedgerOverview } from './components/LedgerOverview';
import { SpendingTrendDetail } from './components/SpendingTrendDetail';
import { TransactionDetail } from './components/TransactionDetail';
import { TransactionForm } from './components/TransactionForm';
import { useLedgerData } from './hooks/useLedgerData';
import {
  getTransactionAmountError,
  isValidTransactionDate,
  isValidTransactionTime,
} from './utils/ledgerTransactionValidation';

type LedgerView = 'overview' | 'trend' | 'detail' | 'category' | 'form';
type LedgerListView = 'overview';
type TransactionFormMode = 'create' | 'edit';

function draftFromTransaction(transaction: LedgerTransaction): LedgerTransactionDraft {
  return {
    transactionDate: transaction.transactionDate,
    transactionTime: transaction.transactionTime,
    transactionType: transaction.transactionType,
    amountText: String(transaction.amount),
    merchantName: transaction.merchantName,
    categoryId: transaction.categoryId,
    paymentMethod: transaction.paymentMethod,
    memo: transaction.memo,
  };
}

function getCurrentMonthStart() {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), 1);
}

function clampToCurrentMonth(month: Date) {
  const currentMonth = getCurrentMonthStart();
  return month.getTime() > currentMonth.getTime() ? currentMonth : month;
}

function toPaymentMethodCode(paymentMethod: string | null) {
  if (!paymentMethod) return 'CASH';

  const normalized = paymentMethod.trim().toUpperCase();
  if (normalized === '현금') return 'CASH';
  if (normalized === '카드') return 'CARD';
  if (normalized === '계좌') return 'ACCOUNT';
  return /^[A-Z0-9_]+$/.test(normalized) ? normalized : 'CASH';
}

export function LedgerScreen() {
  const router = useRouter();
  const { date: deepLinkDate, transactionId: deepLinkTransactionId } = useLocalSearchParams<{
    date?: string;
    transactionId?: string;
  }>();
  const [view, setView] = useState<LedgerView>('overview');
  const [previousListView, setPreviousListView] = useState<LedgerListView>('overview');
  const [formMode, setFormMode] = useState<TransactionFormMode>('create');
  const [currentMonth, setCurrentMonth] = useState(getCurrentMonthStart);
  const visibleMonth = useMemo(() => clampToCurrentMonth(currentMonth), [currentMonth]);
  const {
    categories,
    hiddenTransactions,
    transactions,
    monthlySummary,
    dailyTotals,
    previousDailyTotals,
    categorySummaries,
    isLoading,
    isRefreshing,
    error,
    mutationError,
    refetch,
    isMutating,
    updateTransactionCategory,
    updateTransactionMemo,
    updateTransactionVisibility,
    createManualTransaction,
    updateManualTransaction,
    deleteManualTransaction,
  } = useLedgerData(visibleMonth);
  const [selectedTransactionId, setSelectedTransactionId] = useState<string | null>(null);
  const [draft, setDraft] = useState<LedgerTransactionDraft>({ ...EMPTY_LEDGER_DRAFT });

  const selectedTransaction = [...transactions, ...hiddenTransactions].find(
    (transaction) => transaction.id === selectedTransactionId,
  );
  const needsSelectedTransaction =
    view === 'detail' ||
    view === 'category' ||
    (view === 'form' && formMode === 'edit');
  const activeView =
    needsSelectedTransaction &&
    !selectedTransaction &&
    !isLoading &&
    !isRefreshing &&
    !isMutating
      ? previousListView
      : view;
  const merchantHistory = useMemo(() => {
    if (!selectedTransaction) return [];

    return transactions
      .filter((transaction) => transaction.merchantName === selectedTransaction.merchantName)
      .sort((left, right) =>
        `${right.transactionDate} ${right.transactionTime}`.localeCompare(
          `${left.transactionDate} ${left.transactionTime}`,
        ),
      );
  }, [selectedTransaction, transactions]);

  useEffect(() => {
    if (mutationError) {
      appDialog.alert('거래 내역을 변경하지 못했어요', mutationError.message);
    }
  }, [mutationError]);

  const changeMonth = (offset: number) => {
    setCurrentMonth((current) => {
      const safeCurrent = clampToCurrentMonth(current);
      const next = new Date(
        safeCurrent.getFullYear(),
        safeCurrent.getMonth() + offset,
        1,
      );

      return clampToCurrentMonth(next);
    });
  };

  const selectMonth = (year: number, month: number) => {
    setCurrentMonth(clampToCurrentMonth(new Date(year, month - 1, 1)));
  };

  const openTransaction = (transactionId: string) => {
    setPreviousListView('overview');
    setSelectedTransactionId(transactionId);
    setView('detail');
  };

  const openCreateForm = () => {
    setPreviousListView('overview');
    setFormMode('create');
    const now = new Date();
    setDraft({
      ...EMPTY_LEDGER_DRAFT,
      transactionDate: toDateKey(now),
      transactionTime: toTimeKeyFromParts(now.getHours(), now.getMinutes()),
    });
    setView('form');
  };

  /**
   * 브리핑 등 다른 화면에서 `transactionId`를 달고 들어오면 그 거래의 달로 옮긴 뒤 상세를 편다.
   * 한 번 처리한 파라미터는 지워서, 같은 거래를 다시 눌렀을 때도 정상적으로 열린다.
   */
  useFocusEffect(useCallback(() => {
    if (!deepLinkTransactionId) return;

    if (deepLinkDate) {
      const [year, month] = deepLinkDate.split('-').map(Number);
      if (Number.isInteger(year) && Number.isInteger(month)) {
        setCurrentMonth(clampToCurrentMonth(new Date(year, month - 1, 1)));
      }
    }

    setPreviousListView('overview');
    setSelectedTransactionId(deepLinkTransactionId);
    setView('detail');
    router.setParams({ transactionId: '', date: '' });
  }, [deepLinkDate, deepLinkTransactionId, router]));

  const openEditForm = () => {
    if (!selectedTransaction || selectedTransaction.sourceType !== 'manual') return;

    setFormMode('edit');
    setDraft(draftFromTransaction(selectedTransaction));
    setView('form');
  };

  const openCategoryFromDetail = () => {
    setView('category');
  };

  const selectCategory = async (categoryId: string) => {
    if (!selectedTransactionId) return;

    const apiCategoryId = categories.find(
      (category) => category.id === categoryId,
    )?.apiId;
    if (!apiCategoryId) {
      appDialog.alert('카테고리를 변경하지 못했어요', '카테고리 정보를 다시 불러와주세요.');
      return;
    }

    const response = await updateTransactionCategory(
      selectedTransactionId,
      apiCategoryId,
    );
    if (response) setView('detail');
  };

  const saveTransaction = async () => {
    if (isMutating) return;

    const amountError = getTransactionAmountError(draft.amountText);
    if (amountError) {
      appDialog.alert('금액을 확인해 주세요', amountError);
      return;
    }
    if (!isValidTransactionDate(draft.transactionDate)) {
      appDialog.alert('날짜를 확인해 주세요', '거래일은 오늘까지 선택할 수 있어요.');
      return;
    }
    if (!isValidTransactionTime(draft.transactionTime)) {
      appDialog.alert('시간을 확인해 주세요', '올바른 거래 시간을 선택해 주세요.');
      return;
    }

    const amount = Number(draft.amountText);

    const apiCategoryId = categories.find(
      (category) => category.id === draft.categoryId,
    )?.apiId;
    const request = {
      transactionDate: draft.transactionDate,
      transactionTime: draft.transactionTime,
      transactionType:
        draft.transactionType === 'income' ? ('INCOME' as const) : ('EXPENSE' as const),
      amount,
      merchantName: draft.merchantName.trim() || '직접 입력한 거래',
      categoryId: apiCategoryId,
      paymentMethod: toPaymentMethodCode(draft.paymentMethod),
      memo: draft.memo.trim(),
    };

    if (formMode === 'edit' && selectedTransactionId) {
      const response = await updateManualTransaction(selectedTransactionId, request);
      if (response) setView('detail');
      return;
    }

    const response = await createManualTransaction(request);
    if (response) {
      setSelectedTransactionId(response.transactionId);
      setView(previousListView);
    }
  };

  const deleteTransaction = async () => {
    if (!selectedTransactionId || selectedTransaction?.sourceType !== 'manual') return;

    const response = await deleteManualTransaction(selectedTransactionId);
    if (response !== null) {
      setSelectedTransactionId(null);
      setView(previousListView);
    }
  };

  const toggleTransactionVisibility = async () => {
    if (!selectedTransactionId || !selectedTransaction) return;

    const response = await updateTransactionVisibility(
      selectedTransactionId,
      !selectedTransaction.hidden,
    );
    if (response?.hidden) {
      setSelectedTransactionId(null);
      setView(previousListView);
    }
  };

  const updateMemo = async (memo: string) => {
    if (!selectedTransactionId) return;
    await updateTransactionMemo(selectedTransactionId, memo);
  };

  const closeForm = () => {
    if (isMutating) return;
    setView(formMode === 'edit' ? 'detail' : previousListView);
  };

  const year = visibleMonth.getFullYear();
  const month = visibleMonth.getMonth() + 1;

  if (isLoading || !monthlySummary) {
    return (
      <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
        <AppHeader
          onBackPress={() =>
            router.replace(routes.home)
          }
          showBackButton
          title="가계부"
        />
        <ScreenState
          description={
            error
              ? __DEV__
                ? error.message
                : '네트워크 상태를 확인한 뒤 다시 시도해주세요.'
              : '가계부를 불러오고 있어요'
          }
          isLoading={!error}
          onRetry={error ? () => void refetch() : undefined}
          title={error ? '가계부를 불러오지 못했어요.' : undefined}
        />
      </SafeAreaLayout>
    );
  }

  let content;

  if (activeView === 'trend') {
    content = (
      <SpendingTrendDetail
        categories={categories}
        dailyTotals={dailyTotals}
        month={month}
        onBackPress={() => setView('overview')}
        onRefresh={() => void refetch()}
        previousDailyTotals={previousDailyTotals}
        refreshing={isRefreshing}
        summary={monthlySummary}
        transactions={transactions}
        year={year}
      />
    );
  } else if (activeView === 'detail' && selectedTransaction) {
    content = (
      <TransactionDetail
        categories={categories}
        key={selectedTransaction.id}
        merchantHistory={merchantHistory}
        onBackPress={() => setView(previousListView)}
        onEditPress={openEditForm}
        onOpenCategory={openCategoryFromDetail}
        onToggleHidden={() => void toggleTransactionVisibility()}
        onUpdateMemo={(memo) => void updateMemo(memo)}
        transaction={selectedTransaction}
      />
    );
  } else if (activeView === 'category' && selectedTransaction) {
    content = (
      <CategorySelection
        amount={selectedTransaction.amount}
        categories={categories}
        merchantName={selectedTransaction.merchantName}
        onBackPress={() => setView('detail')}
        onSelect={selectCategory}
        selectedCategoryId={selectedTransaction.categoryId}
        transactionType={selectedTransaction.transactionType}
      />
    );
  } else if (activeView === 'form') {
    content = (
      <TransactionForm
        draft={draft}
        isSubmitting={isMutating}
        mode={formMode}
        onChange={(patch) => setDraft((current) => ({ ...current, ...patch }))}
        onClose={closeForm}
        onDelete={formMode === 'edit' ? deleteTransaction : undefined}
        onSave={saveTransaction}
      />
    );
  } else {
    content = (
      <LedgerOverview
        key={`${year}-${month}`}
        categories={categories}
        categorySummaries={categorySummaries}
        dailyTotals={dailyTotals}
        hiddenTransactions={hiddenTransactions}
        month={month}
        onBackPress={() => router.replace(routes.home)}
        onNextMonth={() => changeMonth(1)}
        onCreateTransaction={openCreateForm}
        onOpenTransaction={openTransaction}
        onOpenTrend={() => setView('trend')}
        onPreviousMonth={() => changeMonth(-1)}
        onSelectMonth={selectMonth}
        previousDailyTotals={previousDailyTotals}
        onRefresh={() => void refetch()}
        refreshing={isRefreshing}
        summary={monthlySummary}
        transactions={transactions}
        year={year}
      />
    );
  }

  return (
    <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
      {content}
    </SafeAreaLayout>
  );
}
