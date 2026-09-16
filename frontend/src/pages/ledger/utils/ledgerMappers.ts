import { getTransactionCategoryIcon } from '@/constants/transactionCategoryIcons';
import { colors } from '@/styles';
import type {
  CategorySpendingApiResponse,
  DailySpendingApiResponse,
  SpendingMonthlySummaryApiResponse,
  TransactionApiResponse,
  TransactionCategoryApiResponse,
} from '@/types/ledgerApi.types';
import type {
  LedgerCategory,
  LedgerCategorySummary,
  LedgerDailyTotal,
  LedgerMonthlySummary,
  LedgerTransaction,
} from '@/types/ledger.types';

function getCategoryId(categoryCode: string | null): string {
  return categoryCode ?? 'ETC';
}

function getTransactionTime(transactionTime: string | null): string {
  return transactionTime?.slice(0, 5) ?? '00:00';
}

export function mapTransactionCategories(
  categories: readonly TransactionCategoryApiResponse[],
): LedgerCategory[] {
  return categories.map((category) => ({
    id: category.code,
    code: category.code,
    apiId: category.categoryId,
    label: category.name,
    icon: getTransactionCategoryIcon(category.code),
  }));
}

export function mapTransaction(
  transaction: TransactionApiResponse,
): LedgerTransaction {
  const isManual = transaction.sourceType === 'MANUAL';
  const isExpense = transaction.transactionType === 'EXPENSE';

  return {
    id: transaction.transactionId,
    transactionDate: transaction.transactionDate,
    transactionTime: getTransactionTime(transaction.transactionTime),
    transactionType:
      transaction.transactionType === 'INCOME' ? 'income' : 'expense',
    amount: transaction.amount,
    merchantName:
      transaction.merchantName ??
      transaction.summary ??
      '거래 내역',
    categoryId: getCategoryId(transaction.categoryCode),
    paymentMethod: transaction.paymentMethod ?? '기타',
    memo: transaction.memo ?? '',
    approvalStatus: isManual ? '수동 등록' : '완료',
    includedInExpense:
      isExpense && transaction.spendingType !== 'NON_SPENDING',
    hidden: transaction.hidden,
    sourceType: isManual ? 'manual' : 'ssafy',
  };
}

export function mapTransactions(
  transactions: readonly TransactionApiResponse[],
): LedgerTransaction[] {
  return transactions.map(mapTransaction);
}

const CATEGORY_COLORS = [
  colors.primary.green,
  colors.ledger.categorySecondary,
  colors.ledger.categoryTertiary,
] as const;

export function mapMonthlySummary(
  summary: SpendingMonthlySummaryApiResponse,
  income: number,
): LedgerMonthlySummary {
  const [year, month] = summary.yearMonth.split('-').map(Number);

  return {
    year,
    month,
    income,
    expense: summary.totalSpending,
    balance: income - summary.totalSpending,
    transactionCount: summary.transactionCount,
    previousMonthDifference: summary.changeFromPreviousMonth,
    dailyAverage: summary.dailyAverage,
    remainingDays: Math.max(summary.daysInMonth - summary.daysElapsed, 0),
  };
}

export function mapDailySpending(
  response: DailySpendingApiResponse,
  incomeByDate: Readonly<Record<string, number>> = {},
): LedgerDailyTotal[] {
  return response.days.map((day) => ({
    date: day.date,
    expense: day.totalSpending,
    income: incomeByDate[day.date] ?? 0,
  }));
}

export function mapCategorySpending(
  response: CategorySpendingApiResponse,
): LedgerCategorySummary[] {
  const categoriesById = new Map<
    string,
    Pick<LedgerCategorySummary, 'categoryId' | 'amount' | 'ratio'>
  >();

  response.categories.forEach((category) => {
    const categoryId = category.code;
    const current = categoriesById.get(categoryId);

    categoriesById.set(categoryId, {
      categoryId,
      amount: (current?.amount ?? 0) + category.totalSpending,
      ratio: (current?.ratio ?? 0) + category.sharePercent,
    });
  });

  const aggregatedCategories = Array.from(categoriesById.values()).sort(
    (left, right) => right.amount - left.amount,
  );

  const topCategories = aggregatedCategories.slice(0, 3);
  const rankedCategoryIds = topCategories
    .filter((category) => category.categoryId !== 'ETC')
    .map((category) => category.categoryId);

  return topCategories.map((category) => ({
    categoryId: category.categoryId,
    amount: category.amount,
    ratio: Math.round(category.ratio),
    color:
      category.categoryId === 'ETC'
        ? colors.ledger.categoryOther
        : CATEGORY_COLORS[rankedCategoryIds.indexOf(category.categoryId)],
  }));
}
