export interface TransactionApiResponse {
  transactionId: string;
  accountId: number | null;
  cardId: number | null;
  categoryId: number | null;
  categoryCode: string | null;
  categoryName: string | null;
  transactionDate: string;
  transactionTime: string | null;
  transactionType: 'INCOME' | 'EXPENSE' | 'TRANSFER';
  amount: number;
  balanceAfter: number | null;
  merchantName: string | null;
  paymentMethod: string | null;
  spendingType: 'FIXED' | 'VARIABLE' | 'NON_SPENDING' | null;
  summary: string | null;
  memo: string | null;
  hidden: boolean;
  sourceType: 'SSAFY' | 'MANUAL' | 'SEED';
  createdAt: string;
  updatedAt: string;
}

export interface TransactionCategoryApiResponse {
  categoryId: number;
  code: string;
  name: string;
  defaultSpendingType: 'FIXED' | 'VARIABLE' | 'NON_SPENDING' | null;
}

export interface SpendingMonthlySummaryApiResponse {
  yearMonth: string;
  totalSpending: number;
  transactionCount: number;
  daysElapsed: number;
  daysInMonth: number;
  dailyAverage: number;
  averagePerTransaction: number;
  previousMonthSpending: number;
  changeFromPreviousMonth: number;
  changeRatePercent: number | null;
}

export interface DailySpendingApiResponse {
  startDate: string;
  endDate: string;
  totalSpending: number;
  transactionCount: number;
  dailyAverage: number;
  days: {
    date: string;
    totalSpending: number;
    transactionCount: number;
  }[];
}

export interface CategorySpendingApiResponse {
  yearMonth: string;
  totalSpending: number;
  transactionCount: number;
  categories: {
    categoryId: number | null;
    code: string;
    name: string;
    totalSpending: number;
    transactionCount: number;
    sharePercent: number;
  }[];
}

export interface CreateManualTransactionRequest {
  transactionDate: string;
  transactionType: 'INCOME' | 'EXPENSE';
  amount: number;
  merchantName?: string;
  categoryId?: number;
  paymentMethod?: string;
  memo?: string;
}

export type UpdateManualTransactionRequest = Partial<CreateManualTransactionRequest>;

export interface CategorySpendingTrendApiResponse {
  startMonth: string;
  endMonth: string;
  category: string | null;
  totalSpending: number;
  monthlyAverage: number;
  months: {
    yearMonth: string;
    totalSpending: number;
    transactionCount: number;
    categories: {
      categoryId: number | null;
      code: string;
      name: string;
      totalSpending: number;
      transactionCount: number;
    }[];
  }[];
}

export interface SavingRatioApiResponse {
  yearMonth: string;
  income: number;
  incomeSource: 'PROFILE' | 'TRANSACTIONS' | 'NONE';
  spending: number;
  savingTransfer: number;
  remaining: number;
  spendingRatioPercent: number | null;
  savingRatioPercent: number | null;
  targetSpendingRatioPercent: number | null;
  targetSavingRatioPercent: number | null;
  exceedsTarget: boolean | null;
}

export interface SpendingTypeBreakdownApiResponse {
  spendingType: 'FIXED' | 'VARIABLE';
  startMonth: string;
  endMonth: string;
  totalAmount: number;
  monthlyAverage: number;
  shareOfSpendingPercent: number;
  latestMonthAmount: number;
  previousAverage: number | null;
  changeRatePercent: number | null;
  months: { yearMonth: string; amount: number; transactionCount: number }[];
  categories: {
    categoryId: number | null;
    code: string;
    name: string;
    amount: number;
    transactionCount: number;
    sharePercent: number;
  }[];
}

export interface PaymentMethodSpendingApiResponse {
  yearMonth: string;
  totalSpending: number;
  transactionCount: number;
  paymentMethods: {
    paymentMethod: string;
    totalSpending: number;
    transactionCount: number;
    sharePercent: number;
  }[];
}

export interface SpendingAnomalyApiResponse {
  recentPeriod: { startMonth: string; endMonth: string; months: number };
  baselinePeriod: { startMonth: string; endMonth: string; months: number };
  thresholdRatePercent: number;
  minimumIncrease: number;
  category: string | null;
  evaluatedCategories: number;
  anomalies: {
    categoryId: number | null;
    code: string;
    name: string;
    recentAverage: number;
    baselineAverage: number;
    increaseAmount: number;
    increaseRatePercent: number;
  }[];
}
