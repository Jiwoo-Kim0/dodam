export interface GoalApiResponse {
  goalId: number;
  name: string;
  type: 'SAVING' | 'SPENDING';
  status: 'ACTIVE' | 'PAUSED' | 'ACHIEVED' | 'CANCELED';
  currentAmount: number;
  targetAmount: number | null;
  targetDate: string | null;
  remainingAmount: number | null;
  progressRatePercent: number | null;
  daysRemaining: number | null;
  monthlyBudget: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface SpendingGoalProgressApiResponse {
  goalId: number;
  goalStatus: string;
  yearMonth: string;
  asOfDate: string;
  daysElapsed: number;
  daysInMonth: number;
  daysLeft: number;
  monthlyBudget: number;
  spent: number;
  remainingBudget: number;
  usageRatePercent: number;
  dailyBudgetRemaining: number | null;
  projectedMonthEnd: number;
  previousMonthSpending: number;
  expectedSavingAmount: number;
  status: 'ON_TRACK' | 'WARNING' | 'EXCEEDED';
}
