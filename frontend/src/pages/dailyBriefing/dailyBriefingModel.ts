import { getTransactionCategoryIcon } from '@/constants/transactionCategoryIcons';
import type {
  CategorySpendingApiResponse,
  DailySpendingApiResponse,
  TransactionApiResponse,
} from '@/types/ledgerApi.types';
import { formatCurrency } from '@/utils/currency';

import type {
  DailyBriefingInsight,
  DailyBriefingStreak,
  DailyBriefingTransaction,
} from './dailyBriefing.types';

function isSpending(transaction: TransactionApiResponse) {
  return transaction.transactionType === 'EXPENSE' &&
    transaction.spendingType !== 'NON_SPENDING' && !transaction.hidden;
}

export function mapBriefingTransactions(transactions: TransactionApiResponse[]): DailyBriefingTransaction[] {
  return transactions
    .filter(isSpending)
    .sort((left, right) => (left.transactionTime ?? '').localeCompare(right.transactionTime ?? ''))
    .map((transaction) => ({
      id: transaction.transactionId,
      time: transaction.transactionTime?.slice(0, 5) ?? '--:--',
      merchant: transaction.merchantName ?? transaction.summary ?? '거래 내역',
      category: transaction.categoryName ?? '미분류',
      amount: transaction.amount,
      icon: getTransactionCategoryIcon(transaction.categoryCode),
    }));
}

export function buildBriefingInsights(
  transactions: TransactionApiResponse[],
  previous: CategorySpendingApiResponse,
  daysInPreviousMonth: number,
): DailyBriefingInsight[] {
  if (previous.totalSpending === 0) return [];

  const yesterdayByCode = new Map<string, { name: string; amount: number }>();
  transactions.filter(isSpending).forEach((transaction) => {
    const code = transaction.categoryCode ?? 'UNCATEGORIZED';
    const current = yesterdayByCode.get(code);
    yesterdayByCode.set(code, {
      name: transaction.categoryName ?? '미분류',
      amount: (current?.amount ?? 0) + transaction.amount,
    });
  });

  const previousByCode = new Map(previous.categories.map((category) => [category.code, category]));
  const candidates = new Set([...yesterdayByCode.keys(), ...previousByCode.keys()]);
  const changes = [...candidates].map((code) => {
    const yesterday = yesterdayByCode.get(code);
    const previousCategory = previousByCode.get(code);
    const baseline = (previousCategory?.totalSpending ?? 0) / daysInPreviousMonth;
    return {
      code,
      name: yesterday?.name ?? previousCategory?.name ?? '미분류',
      amount: yesterday?.amount ?? 0,
      difference: Math.round((yesterday?.amount ?? 0) - baseline),
    };
  });

  const decrease = changes.filter((item) => item.difference < 0)
    .sort((left, right) => left.difference - right.difference)[0];
  const increase = changes.filter((item) => item.difference > 0)
    .sort((left, right) => right.difference - left.difference)[0];

  return [decrease, increase].filter((item) => item !== undefined).map((item) => {
    const type = item.difference < 0 ? 'decrease' : 'increase';
    return {
      id: item.code,
      type,
      label: type === 'decrease' ? '소비 감소' : '소비 증가',
      title: item.amount === 0 ? `${item.name} 지출이 없었어요` : `${item.name} 지출이 평소보다 ${type === 'decrease' ? '적어요' : '많아요'}`,
      description: `지난달 하루 평균보다 ${formatCurrency(Math.abs(item.difference))} ${type === 'decrease' ? '적게' : '많이'} 썼어요`,
      icon: getTransactionCategoryIcon(item.code),
    };
  });
}

export function buildBriefingStreaks(
  daily: DailySpendingApiResponse,
  previousMonthDailyAverage: number | null,
): DailyBriefingStreak[] {
  const recent = [...daily.days].reverse();
  let noSpendDays = 0;
  let underAverageDays = 0;
  for (const day of recent) {
    if (day.totalSpending !== 0) break;
    noSpendDays += 1;
  }
  if (previousMonthDailyAverage !== null) {
    for (const day of recent) {
      if (day.totalSpending > previousMonthDailyAverage) break;
      underAverageDays += 1;
    }
  }

  return [
    noSpendDays > 0 ? {
      id: 'no-spending', label: '지출 없는 날',
      value: noSpendDays === recent.length ? `${noSpendDays}일 이상` : `연속 ${noSpendDays}일`,
      icon: require('@/assets/icons/common/star.png'),
    } : null,
    underAverageDays > 0 ? {
      id: 'under-average', label: '지난달 평균 이하',
      value: underAverageDays === recent.length ? `${underAverageDays}일 이상` : `연속 ${underAverageDays}일`,
      icon: require('@/assets/icons/common/growing.png'),
    } : null,
  ].filter((item): item is DailyBriefingStreak => item !== null);
}
