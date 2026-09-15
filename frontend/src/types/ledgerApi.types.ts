export interface TransactionApiResponse {
  transactionId: string;
  accountId: number | null;
  cardId: number | null;
  categoryId: number | null;
  categoryCode: string | null;
  categoryName: string | null;
  transactionDate: string;
  transactionTime: string;
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