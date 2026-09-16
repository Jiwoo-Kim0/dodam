import { useRouter } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, Text, View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { EMPTY_LEDGER_DRAFT } from '@/mocks/ledger';
import { colors } from '@/styles';
import type {
  LedgerTransaction,
  LedgerTransactionDraft,
} from '@/types/ledger.types';
import { toDateKey, toTimeKeyFromParts } from '@/utils/date';

import { styles } from './LedgerScreen.styles';
import { CategorySelection } from './components/CategorySelection';
import { LedgerCalendar } from './components/LedgerCalendar';
import { LedgerOverview } from './components/LedgerOverview';
import { TransactionDetail } from './components/TransactionDetail';
import { TransactionForm } from './components/TransactionForm';
import { useLedgerData } from './hooks/useLedgerData';

type LedgerView = 'overview' | 'calendar' | 'detail' | 'category' | 'form';
type LedgerListView = 'overview' | 'calendar';
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
      Alert.alert('거래 내역을 변경하지 못했어요', mutationError.message);
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
    setPreviousListView(view === 'calendar' ? 'calendar' : 'overview');
    setSelectedTransactionId(transactionId);
    setView('detail');
  };

  const openCreateForm = () => {
    setPreviousListView(view === 'calendar' ? 'calendar' : 'overview');
    setFormMode('create');
    const now = new Date();
    setDraft({
      ...EMPTY_LEDGER_DRAFT,
      transactionDate: toDateKey(now),
      transactionTime: toTimeKeyFromParts(now.getHours(), now.getMinutes()),
    });
    setView('form');
  };

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
      Alert.alert('카테고리를 변경하지 못했어요', '카테고리 정보를 다시 불러와주세요.');
      return;
    }

    const response = await updateTransactionCategory(
      selectedTransactionId,
      apiCategoryId,
    );
    if (response) setView('detail');
  };

  const saveTransaction = async () => {
    const amount = Number(draft.amountText);
    if (amount <= 0) return;

    const apiCategoryId = categories.find(
      (category) => category.id === draft.categoryId,
    )?.apiId;
    const request = {
      transactionDate: draft.transactionDate,
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
    setView(formMode === 'edit' ? 'detail' : previousListView);
  };

  const year = visibleMonth.getFullYear();
  const month = visibleMonth.getMonth() + 1;

  if (isLoading || !monthlySummary) {
    return (
      <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
        <AppHeader
          onBackPress={() =>
            view === 'calendar'
              ? setView('overview')
              : router.replace(routes.home)
          }
          showBackButton
          title={view === 'calendar' ? '전체 내역' : '가계부'}
        />
        <View style={styles.statusContainer}>
          {error ? (
            <>
              <Text style={styles.statusTitle}>가계부를 불러오지 못했어요.</Text>
              <Text style={styles.statusDescription}>
                {__DEV__
                  ? error.message
                  : '네트워크 상태를 확인한 뒤 다시 시도해주세요.'}
              </Text>
              <Pressable
                accessibilityRole="button"
                onPress={() => void refetch()}
                style={({ pressed }) => [
                  styles.retryButton,
                  pressed && styles.retryButtonPressed,
                ]}>
                <Text style={styles.retryButtonLabel}>다시 시도</Text>
              </Pressable>
            </>
          ) : (
            <ActivityIndicator color={colors.primary.green} size="small" />
          )}
        </View>
      </SafeAreaLayout>
    );
  }

  let content;

  if (view === 'calendar') {
    content = (
      <LedgerCalendar
        categories={categories}
        dailyTotals={dailyTotals}
        hiddenTransactions={hiddenTransactions}
        key={`${year}-${month}`}
        month={month}
        onBackPress={() => setView('overview')}
        onCreateTransaction={openCreateForm}
        onNextMonth={() => changeMonth(1)}
        onOpenTransaction={openTransaction}
        onPreviousMonth={() => changeMonth(-1)}
        onRefresh={() => void refetch()}
        onSelectMonth={selectMonth}
        refreshing={isRefreshing}
        summary={monthlySummary}
        transactions={transactions}
        year={year}
      />
    );
  } else if (view === 'detail' && selectedTransaction) {
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
  } else if (view === 'category' && selectedTransaction) {
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
  } else if (view === 'form') {
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
        categories={categories}
        categorySummaries={categorySummaries}
        dailyTotals={dailyTotals}
        month={month}
        onBackPress={() => router.replace(routes.home)}
        onNextMonth={() => changeMonth(1)}
        onOpenCalendar={() => setView('calendar')}
        onOpenTransaction={openTransaction}
        onPreviousMonth={() => changeMonth(-1)}
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
