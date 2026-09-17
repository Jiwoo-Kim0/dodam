import type { DiagnosisIssueApiResponse } from '@/types/discoveryApi.types';
import type { TransactionApiResponse } from '@/types/ledgerApi.types';

export interface CategoryMerchantSpending {
  name: string;
  amount: number;
  count: number;
}

export interface CategorySpendingQuery {
  categoryCode: string;
  /** 진단이 본 최근 기간. 631(v6)부터는 마지막 완료 월 한 달이다. */
  startDate: string;
  endDate: string;
}

const YEAR_MONTH = /^\d{4}-(0[1-9]|1[0-2])$/;

function lastDayOf(month: string) {
  const [year, monthNumber] = month.split('-').map(Number);
  const day = new Date(Date.UTC(year, monthNumber, 0)).getUTCDate();
  return `${month}-${String(day).padStart(2, '0')}`;
}

/**
 * 진단이 본 최근 기간을 거래 조회용 날짜 범위로 옮긴다. 진단과 같은 기간을 써야
 * 목록에 더해진 금액이 위 비교 카드의 금액과 어긋나지 않는다.
 */
export function categorySpendingQuery(
  issue: DiagnosisIssueApiResponse | null,
): CategorySpendingQuery | null {
  const categoryCode = issue?.currentValue?.categoryCode;
  const recent = issue?.currentValue?.recentPeriod;
  if (
    typeof categoryCode !== 'string' ||
    !categoryCode.trim() ||
    typeof recent !== 'object' ||
    recent === null ||
    !('startMonth' in recent) ||
    !('endMonth' in recent) ||
    typeof recent.startMonth !== 'string' ||
    typeof recent.endMonth !== 'string' ||
    !YEAR_MONTH.test(recent.startMonth) ||
    !YEAR_MONTH.test(recent.endMonth) ||
    recent.startMonth > recent.endMonth
  ) {
    return null;
  }

  return {
    categoryCode: categoryCode.trim(),
    startDate: `${recent.startMonth}-01`,
    endDate: lastDayOf(recent.endMonth),
  };
}

/**
 * 기간 안의 이 카테고리 결제를 지출처별로 묶어 금액이 큰 순으로 돌려준다. 늘었는지 줄었는지는
 * 따지지 않는다 — 그 달에 어디에 썼는지를 그대로 보여주는 목록이다.
 * 진단과 같은 소비 기준으로 거르며, 가맹점명이 없으면 미표시 거래로 묶는다.
 */
export function categoryMonthlySpending(
  transactions: readonly TransactionApiResponse[],
  query: CategorySpendingQuery,
): CategoryMerchantSpending[] {
  const totals = new Map<string, CategoryMerchantSpending>();
  for (const transaction of transactions) {
    if (
      transaction.transactionType !== 'EXPENSE' ||
      transaction.spendingType === 'NON_SPENDING' ||
      transaction.hidden ||
      transaction.categoryCode?.toUpperCase() !== query.categoryCode.toUpperCase() ||
      transaction.transactionDate < query.startDate ||
      transaction.transactionDate > query.endDate ||
      !Number.isFinite(transaction.amount) ||
      transaction.amount <= 0
    ) {
      continue;
    }

    const name = transaction.merchantName?.trim() || '지출처 미표시';
    const previous = totals.get(name);
    totals.set(name, {
      name,
      amount: (previous?.amount ?? 0) + transaction.amount,
      count: (previous?.count ?? 0) + 1,
    });
  }

  return [...totals.values()].sort(
    (a, b) => b.amount - a.amount || a.name.localeCompare(b.name, 'ko'),
  );
}
