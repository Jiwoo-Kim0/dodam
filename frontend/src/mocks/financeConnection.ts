import type { ImageSourcePropType } from 'react-native';

export interface FinancialInstitution {
  id: string;
  name: string;
  logo: ImageSourcePropType;
}

export const FINANCIAL_INSTITUTIONS: readonly FinancialInstitution[] = [
  {
    id: 'kookmin',
    name: 'KB국민은행',
    logo: require('@/assets/icons/banks/kookmin_icon.png'),
  },
  {
    id: 'hana',
    name: '하나은행',
    logo: require('@/assets/icons/banks/hana_icon.png'),
  },
  {
    id: 'shinhan',
    name: '신한은행',
    logo: require('@/assets/icons/banks/shinhan_icon.png'),
  },
  {
    id: 'nonghyup',
    name: 'NH농협은행',
    logo: require('@/assets/icons/banks/nonghyup_icon.png'),
  },
  {
    id: 'woori',
    name: '우리은행',
    logo: require('@/assets/icons/banks/woori_icon.png'),
  },
  {
    id: 'kakaobank',
    name: '카카오뱅크',
    logo: require('@/assets/icons/banks/kakaobank_icon.png'),
  },
  {
    id: 'toss',
    name: '토스뱅크',
    logo: require('@/assets/icons/banks/toss_icon.png'),
  },
  {
    id: 'kbank',
    name: '케이뱅크',
    logo: require('@/assets/icons/banks/kbank_icon.png'),
  },
  {
    id: 'imbank',
    name: 'iM뱅크',
    logo: require('@/assets/icons/banks/imbank_icon.png'),
  },
  {
    id: 'bnk',
    name: 'BNK경남은행',
    logo: require('@/assets/icons/banks/bnk_icon.png'),
  },
  {
    id: 'suhyup',
    name: '수협은행',
    logo: require('@/assets/icons/banks/suhyup_icon.png'),
  },
  {
    id: 'citi',
    name: '한국씨티은행',
    logo: require('@/assets/icons/banks/citi_icon.png'),
  },
];

export const SYNC_INSTITUTIONS = FINANCIAL_INSTITUTIONS.slice(0, 6);

export const SYNC_PROGRESS_BY_ACTIVE_INDEX = [0, 24, 41, 58, 75, 92, 100] as const;
