import type { ImageSourcePropType } from 'react-native';

export type TransactionCategoryCode =
  | 'FOOD'
  | 'CAFE'
  | 'GROCERY'
  | 'PUBLIC_TRANSPORT'
  | 'TAXI'
  | 'SHOPPING'
  | 'BEAUTY'
  | 'CULTURE'
  | 'TRAVEL'
  | 'MEDICAL'
  | 'EDUCATION'
  | 'HOUSING'
  | 'UTILITIES'
  | 'INSURANCE'
  | 'SUBSCRIPTION'
  | 'LOAN_REPAYMENT'
  | 'SAVING'
  | 'TRANSFER'
  | 'INCOME'
  | 'ETC';

const CATEGORY_ICONS: Record<TransactionCategoryCode, ImageSourcePropType> = {
  FOOD: require('@/assets/icons/category/food_icon.png'),
  CAFE: require('@/assets/icons/category/cafeSnack_icon.png'),
  GROCERY: require('@/assets/icons/category/convenienceMart_icon.png'),
  PUBLIC_TRANSPORT: require('@/assets/icons/category/publicTransport_icon.png'),
  TAXI: require('@/assets/icons/category/car_icon.png'),
  SHOPPING: require('@/assets/icons/category/shopping_icon.png'),
  BEAUTY: require('@/assets/icons/category/beauty_icon.png'),
  CULTURE: require('@/assets/icons/category/hobbyLeisure_icon.png'),
  TRAVEL: require('@/assets/icons/category/travelLodging_icon.png'),
  MEDICAL: require('@/assets/icons/category/healthFitness_icon.png'),
  EDUCATION: require('@/assets/icons/category/education_icon.png'),
  HOUSING: require('@/assets/icons/category/housingTelecom_icon.png'),
  UTILITIES: require('@/assets/icons/category/housingTelecom_icon.png'),
  INSURANCE: require('@/assets/icons/category/etc_icon.png'),
  SUBSCRIPTION: require('@/assets/icons/category/subscription_icon.png'),
  LOAN_REPAYMENT: require('@/assets/icons/category/transfer_icon.png'),
  SAVING: require('@/assets/icons/category/transfer_icon.png'),
  TRANSFER: require('@/assets/icons/category/transfer_icon.png'),
  INCOME: require('@/assets/icons/category/transfer_icon.png'),
  ETC: require('@/assets/icons/category/etc_icon.png'),
};

// 가계부 목 데이터가 사용하는 기존 camelCase id도 같은 매퍼에서 처리한다.
const LEGACY_CATEGORY_CODES: Readonly<Record<string, TransactionCategoryCode>> = {
  food: 'FOOD',
  cafeSnack: 'CAFE',
  convenienceMart: 'GROCERY',
  subscription: 'SUBSCRIPTION',
  shopping: 'SHOPPING',
  hobbyLeisure: 'CULTURE',
  healthFitness: 'MEDICAL',
  housingTelecom: 'HOUSING',
  beauty: 'BEAUTY',
  publicTransport: 'PUBLIC_TRANSPORT',
  car: 'TAXI',
  travelLodging: 'TRAVEL',
  education: 'EDUCATION',
  transfer: 'TRANSFER',
  etc: 'ETC',
};

const FALLBACK_CATEGORY_ICON = CATEGORY_ICONS.ETC;

function isTransactionCategoryCode(value: string): value is TransactionCategoryCode {
  return Object.prototype.hasOwnProperty.call(CATEGORY_ICONS, value);
}

/** 백엔드 transaction.categoryCode를 앱의 카테고리 이미지 자산으로 변환한다. */
export function getTransactionCategoryIcon(
  categoryCodeOrLegacyId: string | null | undefined,
): ImageSourcePropType {
  if (!categoryCodeOrLegacyId) return FALLBACK_CATEGORY_ICON;

  const trimmedValue = categoryCodeOrLegacyId.trim();
  const legacyCode = LEGACY_CATEGORY_CODES[trimmedValue];
  if (legacyCode) return CATEGORY_ICONS[legacyCode];

  const normalizedCode = trimmedValue.toUpperCase();
  return isTransactionCategoryCode(normalizedCode)
    ? CATEGORY_ICONS[normalizedCode]
    : FALLBACK_CATEGORY_ICON;
}
