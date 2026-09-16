import type { ImageSourcePropType } from 'react-native';

export type GoalType = 'saving' | 'reducing';

export interface GoalBase {
  id: string;
  type: GoalType;
  title: string;
  dueDateLabel: string;
  dDay: number;
  currentAmount: number;
  targetAmount: number;
  characterImage: ImageSourcePropType;
}

export interface HomeGoal extends GoalBase {
  message: string;
}

export interface GoalHistoryPoint {
  label: string;
  tooltipLabel?: string;
  actualAmount: number;
  plannedAmount: number;
}

export interface SavingGoalDetail extends GoalBase {
  type: 'saving';
  statusAccentText: string;
  statusDescription: string;
  expectedAchievementDate: string;
  monthlyRequiredAmount: number;
  history: readonly GoalHistoryPoint[];
  linkedAccountIds: readonly string[];
}

export interface SpendingCategory {
  id: string;
  label: string;
  amount: number;
}

export interface ReducingGoalDetail extends GoalBase {
  type: 'reducing';
  statusAccentText: string;
  statusDescription: string;
  expectedSavingAmount: number;
  dailyBudget: number;
  history: readonly GoalHistoryPoint[];
  categories: readonly SpendingCategory[];
}

export type GoalDetail = SavingGoalDetail | ReducingGoalDetail;
