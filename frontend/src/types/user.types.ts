/** API 명세 2. User 응답·요청 타입. */

import type { UserRole } from './auth.types';
import type { FinanceConnectionStatus } from './finance.types';

/** GET /v1/users/me */
export interface UserProfile {
  userId: number;
  email: string;
  name: string;
  role: UserRole;
  financeConnectionStatus: FinanceConnectionStatus;
  createdAt: string;
  updatedAt: string;
}

/** 이메일은 금융망 계정 키라 수정할 수 없다. */
export interface UpdateUserProfileRequest {
  name?: string;
}

/** GET /v1/users/me/settings */
export interface UserSettings {
  notificationEnabled: boolean;
  dailyBriefingEnabled: boolean;
  monthlyReportEnabled: boolean;
  /** `HH:mm`. 설정 전이면 null이다. */
  dailyBriefingTime: string | null;
  timezone: string;
  updatedAt: string;
}

/** 모두 선택이며 보내지 않은 항목은 서버가 바꾸지 않는다. */
export interface UpdateUserSettingsRequest {
  notificationEnabled?: boolean;
  dailyBriefingEnabled?: boolean;
  monthlyReportEnabled?: boolean;
  /** `HH:mm` */
  dailyBriefingTime?: string;
  timezone?: string;
}

/** GET /v1/users/me/financial-profile. 미입력 항목은 null이고 금액 단위는 원이다. */
export interface FinancialProfile {
  monthlyIncome: number | null;
  annualIncome: number | null;
  /** 급여일 1~31 */
  payday: number | null;
  fixedExpense: number | null;
  emergencyFundTarget: number | null;
  updatedAt: string | null;
}

/** 모두 선택이며 보내지 않은 항목은 서버가 바꾸지 않는다. */
export interface UpdateFinancialProfileRequest {
  monthlyIncome?: number;
  annualIncome?: number;
  payday?: number;
  fixedExpense?: number;
  emergencyFundTarget?: number;
}
