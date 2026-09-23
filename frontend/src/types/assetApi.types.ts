export interface AccountApiResponse {
  accountId: number;
  bankCode: string;
  bankName: string;
  accountNoMasked: string;
  accountName: string;
  accountType: 'DEMAND_DEPOSIT' | 'MINUS' | 'DEPOSIT' | 'SAVINGS';
  status: 'ACTIVE' | 'INACTIVE' | 'CLOSED';
  balance: number;
  currency: string;
  lastTransactionDate: string | null;
  sourceType: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AccountSummaryApiResponse {
  count: number;
  activeCount: number;
  inactiveCount: number;
  closedCount: number;
  demandDepositCount: number;
  demandDepositBalance: number;
  depositCount: number;
  depositBalance: number;
  savingsCount: number;
  savingsBalance: number;
  minusAccountCount: number;
  minusUsedAmount: number;
  totalAssets: number;
  netAssets: number;
}

export interface AccountBalanceSummaryItemApiResponse {
  accountId: number;
  bankName: string;
  accountNoMasked: string;
  accountName: string;
  status: string;
  balance: number;
  sharePercent: number;
}

export interface AccountBalanceSummaryApiResponse {
  totalCash: number;
  accountCount: number;
  accounts: readonly AccountBalanceSummaryItemApiResponse[];
}

export interface InactiveAccountApiResponse {
  accountId: number;
  bankName: string;
  accountNoMasked: string;
  accountName: string;
  status: string;
  balance: number;
  lastActivityDate: string;
  inactiveMonths: number;
}

export interface InactiveAccountsApiResponse {
  criteria: {
    inactiveMonths: number;
    maxBalance: number | null;
    asOfDate: string;
  };
  count: number;
  totalBalance: number;
  accounts: readonly InactiveAccountApiResponse[];
}

export interface CardWithdrawalAccountApiResponse {
  bankCode: string;
  bankName: string;
  accountName: string;
  accountNoMasked: string;
  accountType: string;
}

export interface CardApiResponse {
  cardId: number;
  cardNoMasked: string;
  cardName: string;
  issuerCode: string;
  issuerName: string;
  status: string;
  creditLimit: number | null;
  annualFee: number | null;
  expiryMonth: string | null;
  withdrawalDay: number | null;
  withdrawalAccount: CardWithdrawalAccountApiResponse | null;
}

export interface CardIssuerSummaryApiResponse {
  issuerCode: string;
  issuerName: string;
  cardCount: number;
  activeCardCount: number;
  totalCreditLimit: number;
}

export interface CardSummaryApiResponse {
  totalCardCount: number;
  activeCardCount: number;
  totalCreditLimit: number;
  totalAnnualFee: number;
  issuers: readonly CardIssuerSummaryApiResponse[];
}

export interface CardPaymentScheduleApiResponse {
  cardNoMasked: string;
  cardName: string;
  billingMonth: string;
  billingDate: string;
  scheduledAmount: number;
  status: string;
}

export interface CardPaymentSchedulesApiResponse {
  startDate: string;
  endDate: string;
  totalScheduledAmount: number;
  schedules: readonly CardPaymentScheduleApiResponse[];
}

export interface LoanApiResponse {
  loanId: number;
  accountNoMasked: string;
  accountName: string;
  institutionCode: string;
  institutionName: string;
  loanType: string;
  repaymentType: string;
  status: string;
  originalPrincipal: number | null;
  balance: number | null;
  interestRate: number | null;
  repaymentDay: number | null;
  nextRepaymentDate: string | null;
  maturityDate: string | null;
}

export interface LoanTypeSummaryApiResponse {
  loanType: string;
  loanCount: number;
  balance: number;
}

export interface LoanSummaryApiResponse {
  loanCount: number;
  totalOriginalPrincipal: number;
  totalBalance: number;
  monthlyRepayment: number;
  weightedAverageRate: number | null;
  nextRepaymentDate: string | null;
  loanTypes: readonly LoanTypeSummaryApiResponse[];
}

export interface DepositSavingApiResponse {
  accountId: number;
  accountName: string;
  bankCode: string;
  accountNoMasked: string;
  type: 'DEPOSIT' | 'SAVINGS';
  status: string;
  balance: number | null;
  interestRate: number | null;
  openingDate: string | null;
  expiryDate: string | null;
  monthlyAmount: number | null;
  detailsAvailable: boolean;
}

export interface DepositSavingSummaryApiResponse {
  totalCount: number;
  totalBalance: number | null;
  depositCount: number;
  depositBalance: number | null;
  savingsCount: number;
  savingsBalance: number | null;
  reason: string | null;
}

export interface InstallmentApiResponse {
  installmentId: number;
  cardId: number;
  merchantName: string;
  totalAmount: number;
  months: number;
  remainingPrincipal: number | null;
  remainingMonths: number;
  monthlyAmount: number;
  nextPaymentDate: string | null;
  status: 'ACTIVE' | 'COMPLETED' | 'CANCELED' | 'OVERDUE';
}

export interface InstallmentMonthApiResponse {
  yearMonth: string;
  scheduledAmount: number;
  installmentCount: number;
}

export interface InstallmentScheduleApiResponse {
  asOfDate: string;
  startMonth: string;
  endMonth: string;
  complete: boolean;
  excludedInstallmentCount: number;
  exclusions: readonly {
    reason: string;
    count: number;
  }[];
  months: readonly InstallmentMonthApiResponse[];
}

/**
 * 계좌 간 이체 요청. 목표 통장에 보내기와 되가져오기가 **같은 API를 방향만 바꿔** 쓴다
 * (docs/goal-transfer-plan.md §3-2).
 *
 * <p>실제 금융망 이체라 같은 요청을 두 번 보내면 두 번 빠져나간다. 호출부에서 전송 중
 * 재요청을 막아야 한다(§3-4).
 */
export interface AccountTransferRequest {
  withdrawalAccountId: number;
  depositAccountId: number;
  amount: number;
}

/**
 * 이체 결과. 갱신된 **두 계좌 잔액만** 담긴다.
 * 목표 진행률은 들어오지 않으므로 목표 상세는 따로 다시 조회해야 한다(§3-5).
 */
export interface AccountTransferApiResponse {
  withdrawalBalance: number;
  depositBalance: number;
  transferredAmount: number;
}
