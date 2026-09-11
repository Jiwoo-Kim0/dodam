export const routes = {
  login: '/login',
  terms: '/terms',
  identityVerification: '/identity-verification',
  financeConnection: '/finance-connection',
  certificateSelection: '/certificate-selection',
  financeSync: '/finance-sync',
  notificationPermission: '/notification-permission',
  home: '/home',
  ledger: '/ledger',
  assets: '/assets',
  discovery: '/discovery',
  report: '/report',
  goalSetup: '/goal-setup',
  goalDetail: (goalId: string) => ({
    pathname: '/home/goals/[id]' as const,
    params: { id: goalId },
  }),
} as const;
