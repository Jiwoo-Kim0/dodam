import type { ImageSourcePropType } from 'react-native';

import {
  FINANCIAL_INSTITUTIONS,
  type FinancialInstitution,
} from '@/mocks/financeConnection';

/**
 * 서버가 준 은행명으로 로고를 가진 금융기관을 찾는다.
 *
 * <p>서버 계좌 응답의 `bankName`(`신한은행`)과 목록에 든 표기(`신한은행`, `KB국민은행`)가
 * 늘 같지 않아 `은행`을 떼고 양쪽으로 포함 관계를 본다. 규칙이 한 곳에만 있어야
 * 같은 계좌가 화면마다 다른 로고로 보이는 일이 없다.
 *
 * <p>못 찾으면 `undefined`다. 로고 자리를 무엇으로 채울지는 화면마다 달라 호출부가 정한다.
 */
export function findInstitutionByBankName(
  bankName: string,
): FinancialInstitution | undefined {
  const normalized = bankName.replace('은행', '');

  return FINANCIAL_INSTITUTIONS.find(({ name }) => {
    const institutionName = name.replace('은행', '');
    return (
      bankName.includes(institutionName) || name.includes(normalized)
    );
  });
}

/** 도담 전용 계좌(세이프박스)는 은행이 아니라 도담 브랜드로 보여준다(705). */
export const SAFEBOX_INSTITUTION_ID = 'dodam';

const DODAM_LOGO: ImageSourcePropType = require('@/assets/logos/dodam_logo_image.png');
const DEFAULT_LOGO: ImageSourcePropType = require('@/assets/icons/common/wallet.png');

/*
 * 은행·카드사 코드(금융망 3자리 코드, 금융상품 회사 코드)와 이름을 로고 식별자로 바꾼다.
 * 자산 목록과 이체 시트가 같은 규칙을 쓰도록 자산 매퍼에서 옮겨 왔다.
 */
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
  '032': 'imbank',
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

export function getInstitutionId(code: string, name: string): string {
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

/** 식별자에 맞는 로고. 모르는 기관은 지갑 아이콘이다. */
export function getInstitutionLogo(institutionId: string): ImageSourcePropType {
  if (institutionId === SAFEBOX_INSTITUTION_ID) return DODAM_LOGO;
  return FINANCIAL_INSTITUTIONS.find(({ id }) => id === institutionId)?.logo ?? DEFAULT_LOGO;
}

/** 계좌 한 건의 로고. 도담 계좌면 도담 로고다. */
export function getAccountLogo(bankCode: string, bankName: string, isSafebox = false): ImageSourcePropType {
  return getInstitutionLogo(isSafebox ? SAFEBOX_INSTITUTION_ID : getInstitutionId(bankCode, bankName));
}
