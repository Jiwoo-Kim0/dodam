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

export interface CreateSavingGoalRequest {
  name: string;
  type: 'SAVING';
  targetAmount: number;
  targetDate: string;
}

export interface CreateSpendingGoalRequest {
  name: string;
  type: 'SPENDING';
  monthlyBudget: number;
}

export type CreateGoalRequest =
  | CreateSavingGoalRequest
  | CreateSpendingGoalRequest;

export interface UpdateGoalRequest {
  name?: string;
  targetAmount?: number;
  targetDate?: string;
  monthlyBudget?: number;
  status?: 'ACTIVE' | 'PAUSED' | 'ACHIEVED' | 'CANCELED';
}

export interface GoalProgressApiResponse {
  goalId: number;
  status: string;
  targetAmount: number;
  currentAmount: number;
  remainingAmount: number;
  progressRatePercent: number;
  startDate: string;
  targetDate: string;
  asOfDate: string;
  totalDays: number;
  daysElapsed: number;
  daysRemaining: number;
  expectedProgressRatePercent: number;
  gapPercentPoint: number;
  paceStatus: 'AHEAD' | 'ON_TRACK' | 'BEHIND' | 'ACHIEVED';
  linkedAccountCount: number;
}

export interface RequiredSavingApiResponse {
  goalId: number;
  targetAmount: number;
  currentAmount: number;
  remainingAmount: number;
  asOfDate: string;
  targetDate: string;
  monthsRemaining: number;
  daysRemaining: number;
  requiredMonthlySaving: number;
  requiredDailySaving: number;
  monthsElapsed: number;
  averageMonthlySaving: number;
  monthlyShortfall: number;
  feasibility:
    | 'ACHIEVED'
    | 'SUFFICIENT'
    | 'INSUFFICIENT'
    | 'DEADLINE_PASSED';
  requiredRatioOfIncomePercent: number | null;
}

export interface GoalForecastApiResponse {
  goalId: number;
  targetAmount: number;
  currentAmount: number;
  remainingAmount: number;
  baseDate: string;
  targetDate: string;
  averageMonthlySaving: number;
  monthsToAchieve: number | null;
  expectedAchievementDate: string | null;
  daysDifference: number | null;
  onSchedule: boolean;
  projection: {
    yearMonth: string;
    projectedAmount: number;
    achieved: boolean;
  }[];
}

export type GoalRiskLevel = 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH';

export interface GoalRiskApiResponse {
  goalId: number;
  type: 'SAVING' | 'SPENDING';
  goalStatus: 'ACTIVE' | 'PAUSED' | 'ACHIEVED' | 'CANCELED';
  riskLevel: GoalRiskLevel;
  factors: string[];
  metrics: {
    requiredMonthlySaving: number | null;
    averageMonthlySaving: number | null;
    progressRatePercent: number | null;
    expectedProgressRatePercent: number | null;
    monthlyBudget: number | null;
    spent: number | null;
    projectedMonthEnd: number | null;
    usageRatePercent: number | null;
  };
}

export interface GoalAccountApiResponse {
  accountId: number;
  bankCode: string;
  bankName: string;
  accountNoMasked: string;
  accountName: string;
  accountType: string;
  status: string;
  balance: number;
  contributionAmount: number | null;
}

export interface GoalAccountsApiResponse {
  goalId: number;
  linkedCount: number;
  totalBalance: number;
  accounts: GoalAccountApiResponse[];
  page: number;
  size: number;
  totalPages: number;
  last: boolean;
}

export interface UpdateGoalAccountsRequest {
  accountNos: string[];
}
