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

export interface CardWithdrawalAccountApiResponse {
  bankCode: string;
  bankName: string;
  accountName: string;
  accountNoMasked: string;
  accountType: string;
}

export interface CardApiResponse {
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
