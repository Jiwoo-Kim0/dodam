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
