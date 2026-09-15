import { useRouter } from 'expo-router';
import { useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import {
  EMPTY_LEDGER_DRAFT,
  LEDGER_CATEGORIES,
} from '@/mocks/ledger';
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

export function LedgerScreen() {
  const router = useRouter();
  const [view, setView] = useState<LedgerView>('overview');
  const [previousListView, setPreviousListView] = useState<LedgerListView>('overview');
  const [formMode, setFormMode] = useState<TransactionFormMode>('create');
  const [currentMonth, setCurrentMonth] = useState(() => {
    const now = new Date();
    return new Date(now.getFullYear(), now.getMonth(), 1);
  });
  const {
    transactions,
    monthlySummary,
    dailyTotals,
    categorySummaries,
    isLoading,
    isRefreshing,
    error,
    refetch,
    updateTransaction,
    prependTransaction,
    removeTransaction,
  } = useLedgerData(currentMonth);
  const [selectedTransactionId, setSelectedTransactionId] = useState<string | null>(null);
  const [draft, setDraft] = useState<LedgerTransactionDraft>({ ...EMPTY_LEDGER_DRAFT });

  const selectedTransaction = transactions.find(
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

  const updateSelectedTransaction = (patch: Partial<LedgerTransaction>) => {
    if (!selectedTransactionId) return;

    updateTransaction(selectedTransactionId, patch);
  };

  const changeMonth = (offset: number) => {
    setCurrentMonth(
      (current) => new Date(current.getFullYear(), current.getMonth() + offset, 1),
    );
  };

  const selectMonth = (year: number, month: number) => {
    setCurrentMonth(new Date(year, month - 1, 1));
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

  const selectCategory = (categoryId: string) => {
    updateSelectedTransaction({ categoryId });
    setView('detail');
  };

  const saveTransaction = () => {
    const amount = Number(draft.amountText);
    if (amount <= 0) return;

    if (formMode === 'edit' && selectedTransactionId) {
      updateSelectedTransaction({
        transactionDate: draft.transactionDate,
        transactionTime: draft.transactionTime,
        transactionType: draft.transactionType,
        amount,
        merchantName: draft.merchantName.trim() || '직접 입력한 거래',
        categoryId: draft.categoryId ?? 'etc',
        paymentMethod: draft.paymentMethod ?? '현금',
        memo: draft.memo.trim(),
      });
      setView('detail');
      return;
    }

    const transactionId = `manual-${Date.now()}`;
    const newTransaction: LedgerTransaction = {
      id: transactionId,
      transactionDate: draft.transactionDate,
      transactionTime: draft.transactionTime,
      transactionType: draft.transactionType,
      amount,
      merchantName: draft.merchantName.trim() || '직접 입력한 거래',
      categoryId: draft.categoryId ?? 'etc',
      paymentMethod: draft.paymentMethod ?? '현금',
      memo: draft.memo.trim(),
      approvalStatus: '수동 등록',
      includedInExpense: draft.transactionType === 'expense',
      hidden: false,
      sourceType: 'manual',
    };

    prependTransaction(newTransaction);
    setSelectedTransactionId(transactionId);
    setView(previousListView);
  };

  const deleteTransaction = () => {
    if (!selectedTransactionId || selectedTransaction?.sourceType !== 'manual') return;

    removeTransaction(selectedTransactionId);
    setSelectedTransactionId(null);
    setView(previousListView);
  };

  const closeForm = () => {
    setView(formMode === 'edit' ? 'detail' : previousListView);
  };

  const year = currentMonth.getFullYear();
  const month = currentMonth.getMonth() + 1;

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
                네트워크 상태를 확인한 뒤 다시 시도해주세요.
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
        categories={LEDGER_CATEGORIES}
        dailyTotals={dailyTotals}
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
        categories={LEDGER_CATEGORIES}
        key={selectedTransaction.id}
        merchantHistory={merchantHistory}
        onBackPress={() => setView(previousListView)}
        onEditPress={openEditForm}
        onOpenCategory={openCategoryFromDetail}
        onToggleHidden={() => updateSelectedTransaction({ hidden: !selectedTransaction.hidden })}
        onToggleIncluded={() =>
          updateSelectedTransaction({
            includedInExpense: !selectedTransaction.includedInExpense,
          })
        }
        onUpdateMemo={(memo) => updateSelectedTransaction({ memo })}
        transaction={selectedTransaction}
      />
    );
  } else if (view === 'category' && selectedTransaction) {
    content = (
      <CategorySelection
        amount={selectedTransaction.amount}
        categories={LEDGER_CATEGORIES}
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
        categories={LEDGER_CATEGORIES}
        categorySummaries={categorySummaries}
        month={month}
        onBackPress={() => router.replace(routes.home)}
        onNextMonth={() => changeMonth(1)}
        onOpenCalendar={() => setView('calendar')}
        onOpenTransaction={openTransaction}
        onPreviousMonth={() => changeMonth(-1)}
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
