export interface GoalAccount {
  id: string;
  institutionId: string;
  displayName: string;
  maskedAccountNumber: string;
}

export const MOCK_GOAL_ACCOUNTS: readonly GoalAccount[] = [
  {
    id: 'goal-account-kookmin',
    institutionId: 'kookmin',
    displayName: '국민',
    maskedAccountNumber: '123456-78-******',
  },
  {
    id: 'goal-account-hana',
    institutionId: 'hana',
    displayName: '하나',
    maskedAccountNumber: '321654-12-******',
  },
  {
    id: 'goal-account-shinhan',
    institutionId: 'shinhan',
    displayName: '신한',
    maskedAccountNumber: '110-456-******',
  },
  {
    id: 'goal-account-nonghyup',
    institutionId: 'nonghyup',
    displayName: '농협',
    maskedAccountNumber: '356-7890-******',
  },
];
