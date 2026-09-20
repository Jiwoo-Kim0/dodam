export const routes = {
  login: '/login',
  terms: '/terms',
  identityVerification: '/identity-verification',
  financeConnection: '/finance-connection',
  certificateSelection: '/certificate-selection',
  financeSync: '/finance-sync',
  notificationPermission: '/notification-permission',
  home: '/home',
  dailyBriefing: '/home/daily-briefing',
  notifications: '/home/notifications',
  subscriptions: '/home/subscriptions',
  ledger: '/ledger',
  /** 가계부를 해당 거래의 달로 연 뒤 그 거래 상세를 바로 펼친다. `date`는 YYYY-MM-DD다. */
  ledgerTransaction: (transactionId: string, date: string) => ({
    pathname: '/ledger' as const,
    params: { transactionId, date },
  }),
  assets: '/assets',
  discovery: '/discovery',
  discoveryInsights: '/discovery/insights',
  discoveryInsightDetail: (insightId: string) => ({
    pathname: '/discovery/insights/[id]' as const,
    params: { id: insightId },
  }),
  report: '/report',
  reportPeerComparison: '/report/peer-comparison',
  reportEfficiencyScore: '/report/efficiency-score',
  /** 도리에게 묻기. 탭 바깥이라 어느 탭에서 열어도 뒤로가기가 왔던 자리로 돌아간다. */
  coachChat: '/coach-chat',
  coachChatFromIssue: (diagnosisId: number, issueId: number) => ({
    pathname: '/coach-chat' as const,
    params: { diagnosisId, issueId },
  }),
  goalSetup: '/goal-setup',
  goalSetupEdit: (goalId: string) => ({
    pathname: '/goal-setup' as const,
    params: { goalId, mode: 'edit' },
  }),
  goalDetail: (goalId: string) => ({
    pathname: '/home/goals/[id]' as const,
    params: { id: goalId },
  }),
} as const;
