import type { ImageSourcePropType } from 'react-native';

/**
 * 정기결제 서비스 아이콘.
 *
 * 서버가 서비스 카탈로그(`SubscriptionServiceCatalog`)에서 이름을 맞춰 `iconKey`를 내려주고
 * (backend/docs/subscription.md), 그 값이 아래 자산 이름과 같다. 화면은 이름을 보지 않는다.
 * 카탈로그에 없는 서비스, 아이콘이 없는 서비스는 `iconKey`가 null로 와서 기타 아이콘으로 모인다.
 */
const SERVICE_ICONS: Readonly<Record<string, ImageSourcePropType>> = {
  baemin: require('@/assets/icons/payments/baemin 1.png'),
  chatgpt: require('@/assets/icons/payments/chatgpt 1.png'),
  coupang: require('@/assets/icons/payments/coupang 1.png'),
  millie: require('@/assets/icons/payments/millie 1.png'),
  naver: require('@/assets/icons/payments/naver 1.png'),
  netflix: require('@/assets/icons/payments/netflix 1.png'),
  rocket: require('@/assets/icons/payments/rocket 1.png'),
  spotify: require('@/assets/icons/payments/spotify 1.png'),
  'ssg-com': require('@/assets/icons/payments/ssg-com 1.png'),
  yogiyo: require('@/assets/icons/payments/yogiyo 1.png'),
  youtube: require('@/assets/icons/payments/youtube 1.png'),
};

/** 짝이 없는 서비스가 모두 쓰는 아이콘. 가계부 기타 카테고리와 같은 자산이다. */
const FALLBACK_SERVICE_ICON: ImageSourcePropType = require('@/assets/icons/category/etc_icon.png');

export function getSubscriptionServiceIcon(
  iconKey: string | null | undefined,
): ImageSourcePropType {
  if (!iconKey) return FALLBACK_SERVICE_ICON;

  return SERVICE_ICONS[iconKey] ?? FALLBACK_SERVICE_ICON;
}
