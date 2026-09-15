import { colors } from '@/styles';
import type {
  CategorySpendingApiResponse,
  DailySpendingApiResponse,
  SpendingMonthlySummaryApiResponse,
  TransactionApiResponse,
} from '@/types/ledgerApi.types';
import type {
  LedgerCategorySummary,
  LedgerDailyTotal,
  LedgerMonthlySummary,
  LedgerTransaction,
} from '@/types/ledger.types';

const CATEGORY_ID_BY_CODE: Readonly<Record<string, string>> = {
  FOOD: 'food',
  CAFE: 'cafeSnack',
  GROCERY: 'convenienceMart',
  CONVENIENCE: 'convenienceMart',
  SUBSCRIPTION: 'subscription',
  SHOPPING: 'shopping',
  CULTURE: 'hobbyLeisure',
  MEDICAL: 'healthFitness',
  HOUSING: 'housingTelecom',
  UTILITIES: 'housingTelecom',
  BEAUTY: 'beauty',
  PUBLIC_TRANSPORT: 'publicTransport',
  TAXI: 'publicTransport',
  TRANSPORT: 'publicTransport',
  CAR: 'car',
  TRAVEL: 'travelLodging',
  EDUCATION: 'education',
  TRANSFER: 'transfer',
  ETC: 'etc',
  UNCATEGORIZED: 'etc',
};

function getCategoryId(categoryCode: string | null): string {
  if (!categoryCode) {
    return 'etc';
  }

  return CATEGORY_ID_BY_CODE[categoryCode] ?? 'etc';
}

function getTransactionTime(transactionTime: string): string {
  return transactionTime.slice(0, 5);
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
  colors.ledger.expense,
  colors.primary.orange,
  colors.primary.yellow,
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
    const categoryId = getCategoryId(category.code);
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
  const visibleCategories = aggregatedCategories
    .filter((category) => category.categoryId !== 'etc')
    .slice(0, 2);
  const visibleCategoryIds = new Set(
    visibleCategories.map((category) => category.categoryId),
  );
  const remainingCategories = aggregatedCategories.filter(
    (category) => !visibleCategoryIds.has(category.categoryId),
  );

  const summaries: LedgerCategorySummary[] = visibleCategories.map(
    (category, index) => ({
      categoryId: category.categoryId,
      amount: category.amount,
      ratio: Math.round(category.ratio),
      color: CATEGORY_COLORS[index],
    }),
  );

  if (remainingCategories.length > 0) {
    const otherAmount = remainingCategories.reduce(
      (total, category) => total + category.amount,
      0,
    );
    const otherRatio = remainingCategories.reduce(
      (total, category) => total + category.ratio,
      0,
    );

    summaries.push({
      categoryId: 'etc',
      amount: otherAmount,
      ratio: Math.round(otherRatio),
      color: CATEGORY_COLORS[2],
    });
  }

  return summaries;
}
