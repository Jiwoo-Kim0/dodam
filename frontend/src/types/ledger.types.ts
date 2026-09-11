import type { ImageSourcePropType } from 'react-native';

export type LedgerTransactionType = 'expense' | 'income';
export type LedgerTransactionSource = 'ssafy' | 'manual';

export interface LedgerCategory {
  id: string;
  label: string;
  icon: ImageSourcePropType;
}

export interface LedgerCategorySummary {
  categoryId: string;
  amount: number;
  ratio: number;
  color: string;
}

export interface LedgerDailyTotal {
  date: string;
  expense?: number;
  income?: number;
}

export interface LedgerTransaction {
  id: string;
  transactionDate: string;
  transactionTime: string;
  transactionType: LedgerTransactionType;
  amount: number;
  merchantName: string;
  categoryId: string;
  paymentMethod: string;
  memo: string;
  approvalStatus: string;
  includedInExpense: boolean;
  hidden: boolean;
  sourceType: LedgerTransactionSource;
}

export interface LedgerTransactionDraft {
  transactionDate: string;
  transactionTime: string;
  transactionType: LedgerTransactionType;
  amountText: string;
  merchantName: string;
  categoryId: string | null;
  paymentMethod: string | null;
  memo: string;
}

export interface LedgerMonthlySummary {
  year: number;
  month: number;
  income: number;
  expense: number;
  balance: number;
  previousMonthDifference: number;
  dailyAverage: number;
  remainingDays: number;
}
