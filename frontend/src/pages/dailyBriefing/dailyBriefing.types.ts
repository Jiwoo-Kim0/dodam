import type { ImageSourcePropType } from 'react-native';

export interface DailyBriefingTransaction {
  id: string;
  time: string;
  merchant: string;
  category: string;
  amount: number;
  icon: ImageSourcePropType;
}

export interface DailyBriefingInsight {
  id: string;
  type: 'decrease' | 'increase';
  label: string;
  title: string;
  description: string;
  icon: ImageSourcePropType;
}

export interface DailyBriefingGoalImpact {
  id: string;
  label: string;
  value: string;
  icon: ImageSourcePropType;
}

export interface DailyBriefingStreak {
  id: string;
  label: string;
  value: string;
}

export interface DailyBriefingData {
  date: string;
  yesterdaySpentAmount: number;
  previousMonthDailyAverage: number | null;
  comparisonMessage: string | null;
  transactions: DailyBriefingTransaction[];
  transactionsMessage: string | null;
  insights: DailyBriefingInsight[];
  insightsMessage: string | null;
  goalImpacts: DailyBriefingGoalImpact[];
  goalImpactsMessage: string | null;
  upcomingEvent: { title: string; description: string };
  streaks: DailyBriefingStreak[];
  streaksMessage: string | null;
}
