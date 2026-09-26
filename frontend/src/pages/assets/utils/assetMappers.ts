import type {
  AccountApiResponse,
  AccountSummaryApiResponse,
  CardApiResponse,
  CardPaymentSchedulesApiResponse,
  DepositSavingApiResponse,
  DepositSavingSummaryApiResponse,
  LoanApiResponse,
  LoanSummaryApiResponse,
} from '@/types/assetApi.types';
import type { SafeboxAccountResponse } from '@/api/safeboxApi';
import { formatCurrency } from '@/utils/currency';
import type {
  AssetItem,
  AssetSection,
} from '@/types/asset.types';

const INSTITUTION_ID_BY_CODE: Readonly<Record<string, string>> = {
  '004': 'kookmin',
  KB: 'kookmin',
  KB_KOOKMIN: 'kookmin',
  KB_CARD: 'kookmin',

  '081': 'hana',
  HANA: 'hana',
  HANA_BANK: 'hana',
  HANA_CARD: 'hana',

  '088': 'shinhan',
  SHINHAN: 'shinhan',
  SHINHAN_BANK: 'shinhan',
  SHINHAN_CARD: 'shinhan',

  '011': 'nonghyup',
  NH: 'nonghyup',
  NH_BANK: 'nonghyup',
  NH_CARD: 'nonghyup',

  '020': 'woori',
  WOORI: 'woori',
  WOORI_BANK: 'woori',
  WOORI_CARD: 'woori',

  '090': 'kakaobank',
  KAKAOBANK: 'kakaobank',
  KAKAO_BANK: 'kakaobank',

  '092': 'toss',
  TOSS: 'toss',
  TOSS_BANK: 'toss',

  '089': 'kbank',
  KBANK: 'kbank',
  K_BANK: 'kbank',

  '031': 'imbank',
  IMBANK: 'imbank',
  IM_BANK: 'imbank',
  DGB: 'imbank',

  '039': 'bnk',
  BNK: 'bnk',
  BNK_KYONGNAM: 'bnk',

  '007': 'suhyup',
  SUHYUP: 'suhyup',
  SUHYUP_BANK: 'suhyup',

  '027': 'citi',
  CITI: 'citi',
  CITI_BANK: 'citi',
};

function getInstitutionId(code: string, name: string): string {
  const institutionId = INSTITUTION_ID_BY_CODE[code.toUpperCase()];

  if (institutionId) {
    return institutionId;
  }

  if (name.includes('국민')) return 'kookmin';
  if (name.includes('하나')) return 'hana';
  if (name.includes('신한')) return 'shinhan';
  if (name.includes('농협')) return 'nonghyup';
  if (name.includes('우리')) return 'woori';
  if (name.includes('카카오')) return 'kakaobank';
  if (name.includes('토스')) return 'toss';
  if (name.includes('케이')) return 'kbank';
  if (name.includes('iM') || name.includes('대구')) return 'imbank';
  if (name.includes('경남') || name.includes('부산')) return 'bnk';
  if (name.includes('수협')) return 'suhyup';
  if (name.includes('씨티')) return 'citi';

  return code.toLowerCase();
}

function formatMonthDay(date: string): string {
  const [, month, day] = date.split('-');

  return month && day ? `${month}.${day}` : date;
}

function mapCheckingAccount(account: AccountApiResponse): AssetItem {
  return {
    id: `account-${account.accountId}`,
    institutionId: getInstitutionId(account.bankCode, account.bankName),
    productName: account.accountName,
    description: account.accountNoMasked,
    amount: account.balance,
  };
}

function mapDepositSaving(
  depositSaving: DepositSavingApiResponse,
): AssetItem {
  const rate =
    depositSaving.interestRate === null
      ? null
      : `연 ${depositSaving.interestRate}%`;

  return {
    id: `deposit-saving-${depositSaving.accountId}`,
    institutionId: getInstitutionId(depositSaving.bankCode, ''),
    productName: depositSaving.accountName,
    description: [depositSaving.accountNoMasked, rate]
      .filter(Boolean)
      .join(' · '),
    amount: depositSaving.balance ?? 0,
  };
}

function mapLoan(loan: LoanApiResponse): AssetItem {
  const rate =
    loan.interestRate === null ? null : `연 ${loan.interestRate}%`;

  return {
    id: `loan-${loan.accountNoMasked}`,
    institutionId: getInstitutionId(
      loan.institutionCode,
      loan.institutionName,
    ),
    productName: loan.accountName,
    description: [loan.accountNoMasked, rate].filter(Boolean).join(' · '),
    amount: -Math.abs(loan.balance ?? 0),
  };
}

function mapCard(
  card: CardApiResponse,
  paymentSchedules: CardPaymentSchedulesApiResponse,
): AssetItem {
  const schedule = paymentSchedules.schedules.find(
    ({ cardNoMasked }) => cardNoMasked === card.cardNoMasked,
  );

  const paymentDate = schedule
    ? `${formatMonthDay(schedule.billingDate)} 결제 예정`
    : null;

  return {
    id: `card-${card.cardNoMasked}`,
    institutionId: getInstitutionId(card.issuerCode, card.issuerName),
    productName: card.cardName,
    description: [card.cardNoMasked, paymentDate]
      .filter(Boolean)
      .join(' · '),
    amount: schedule?.scheduledAmount ?? 0,
  };
}

interface MapAssetSectionsParams {
  accounts: readonly AccountApiResponse[];
  accountSummary: AccountSummaryApiResponse;
  depositSavings: readonly DepositSavingApiResponse[];
  depositSavingSummary: DepositSavingSummaryApiResponse;
  loans: readonly LoanApiResponse[];
  loanSummary: LoanSummaryApiResponse;
  cards: readonly CardApiResponse[];
  cardPaymentSchedules: CardPaymentSchedulesApiResponse;
  safeboxAccount: SafeboxAccountResponse | null;
}

/** 도담 계좌는 목표별 세이프박스가 담긴 전용 계좌라 일반 입출금과 나눠 보여준다(705). */
function mapSafeboxAccount(
  account: AccountApiResponse,
  safebox: SafeboxAccountResponse,
): AssetItem {
  return {
    ...mapCheckingAccount(account),
    description: `${safebox.accountNoMasked} · 목표에 담은 돈 ${formatCurrency(safebox.allocatedAmount)}`,
  };
}

export function mapAssetSections({
  accounts,
  accountSummary,
  depositSavings,
  depositSavingSummary,
  loans,
  loanSummary,
  cards,
  cardPaymentSchedules,
  safeboxAccount,
}: MapAssetSectionsParams): readonly AssetSection[] {
  const safeboxRow = safeboxAccount
    ? accounts.find(({ accountId }) => accountId === safeboxAccount.accountId)
    : undefined;
  const safeboxSections: AssetSection[] = safeboxAccount && safeboxRow
    ? [{
        id: 'safebox',
        title: '도담 계좌',
        items: [mapSafeboxAccount(safeboxRow, safeboxAccount)],
      }]
    : [];

  return [
    {
      id: 'checking',
      title: '입출금',
      // 계좌 요약의 입출금 합계에는 도담 계좌가 들어 있어 따로 보여주는 만큼 뺀다.
      summaryAmount: accountSummary.demandDepositBalance - (safeboxRow?.balance ?? 0),
      items: accounts
        .filter(({ accountId, accountType }) =>
          accountType === 'DEMAND_DEPOSIT' && accountId !== safeboxRow?.accountId)
        .map(mapCheckingAccount),
    },
    ...safeboxSections,
    {
      id: 'saving',
      title: '예·적금',
      summaryAmount: depositSavingSummary.totalBalance ?? 0,
      items: depositSavings.map(mapDepositSaving),
    },
    {
      id: 'loan',
      title: '대출',
      summaryAmount: -Math.abs(loanSummary.totalBalance),
      items: loans.map(mapLoan),
    },
    {
      id: 'card',
      title: '카드',
      summaryPrefix: '이번 달 결제 예정',
      summaryAmount: cardPaymentSchedules.totalScheduledAmount,
      items: cards.map((card) =>
        mapCard(card, cardPaymentSchedules),
      ),
    },
  ];
}
