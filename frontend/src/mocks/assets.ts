import { MOCK_GOAL_ACCOUNTS } from '@/mocks/goalAccounts';
import type { AssetSection } from '@/types/asset.types';

function findGoalAccount(accountId: string) {
  const account = MOCK_GOAL_ACCOUNTS.find(({ id }) => id === accountId);

  if (!account) {
    throw new Error(`목표 계좌를 찾을 수 없습니다: ${accountId}`);
  }

  return account;
}

const kookminAccount = findGoalAccount('goal-account-kookmin');
const hanaAccount = findGoalAccount('goal-account-hana');
const shinhanAccount = findGoalAccount('goal-account-shinhan');
const nonghyupAccount = findGoalAccount('goal-account-nonghyup');

export const MOCK_ASSET_OVERVIEW = {
  totalAsset: 8795830,
  changeFromLastMonth: 234000,
  composition: [
    { label: '예·적금', amount: 6200000, tone: 'strong' },
    { label: '입출금', amount: 1595830, tone: 'medium' },
    { label: '대출', amount: 1000000, tone: 'light' },
  ],
} as const;

export const MOCK_ASSET_SECTIONS: readonly AssetSection[] = [
  {
    id: 'checking',
    title: '입출금',
    items: [
      {
        id: kookminAccount.id,
        institutionId: kookminAccount.institutionId,
        productName: 'KB국민ONE통장',
        description: '연 0.1% · 주거래',
        amount: 2600000,
      },
      {
        id: hanaAccount.id,
        institutionId: hanaAccount.institutionId,
        productName: '하나 주거래 우대통장',
        description: '연 0.1%',
        amount: 600000,
      },
    ],
  },
  {
    id: 'saving',
    title: '예·적금',
    items: [
      {
        id: shinhanAccount.id,
        institutionId: shinhanAccount.institutionId,
        productName: '신한 청년희망적금',
        description: '연 2.1% · 만기 3개월',
        amount: 2400000,
      },
      {
        id: nonghyupAccount.id,
        institutionId: nonghyupAccount.institutionId,
        productName: 'NH올원e통장',
        description: '연 4.6% · 만기 8개월',
        amount: 1800000,
      },
    ],
  },
  {
    id: 'loan',
    title: '대출',
    items: [
      {
        id: 'loan-kakaobank',
        institutionId: 'kakaobank',
        productName: '카카오뱅크 비상금 대출',
        description: '연 7% · 32개월 남음',
        amount: -6000000,
      },
    ],
  },
  {
    id: 'card',
    title: '카드',
    summaryPrefix: '이달 결제 예정',
    items: [
      {
        id: 'card-kookmin',
        institutionId: 'kookmin',
        productName: 'KB국민카드 (주 사용)',
        description: '09.13 결제 예정',
        amount: 612000,
      },
      {
        id: 'card-hana',
        institutionId: 'hana',
        productName: '하나카드',
        description: '09.13 결제 예정',
        amount: 250000,
      },
    ],
  },
];
