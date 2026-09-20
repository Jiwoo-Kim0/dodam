import type { ImageSourcePropType } from 'react-native';

/**
 * 정기결제 서비스 이름을 브랜드 아이콘으로 바꾼다.
 *
 * 서버가 아이콘 키를 주지 않고 서비스 이름만 문자열로 내려주므로(backend/docs/subscription.md)
 * 이름을 정규화해 키워드로 찾는다. 짝이 없거나 애매한 이름은 모두 기타 아이콘 하나로 모은다.
 */

/** 공백·중점·하이픈 등 표기 차이를 지운 비교용 문자열을 만든다. "쿠팡 와우" → "쿠팡와우" */
function normalize(name: string): string {
  return name.toLowerCase().replace(/[\s·・‧・.,'"()\-_/+]/g, '');
}

/**
 * 위에서부터 먼저 걸리는 규칙을 쓴다. 좁은 이름이 넓은 이름보다 앞에 와야 한다.
 * 예: "쿠팡 와우"는 로켓 아이콘, 그냥 "쿠팡"은 쿠팡 로고다.
 */
const SERVICE_ICON_RULES: readonly {
  icon: ImageSourcePropType;
  keywords: readonly string[];
}[] = [
  {
    icon: require('@/assets/icons/payments/netflix 1.png'),
    keywords: ['넷플릭스', 'netflix'],
  },
  {
    icon: require('@/assets/icons/payments/youtube 1.png'),
    keywords: ['유튜브', 'youtube'],
  },
  {
    icon: require('@/assets/icons/payments/rocket 1.png'),
    keywords: ['쿠팡와우', '로켓와우', '와우멤버십'],
  },
  {
    icon: require('@/assets/icons/payments/coupang 1.png'),
    keywords: ['쿠팡', 'coupang'],
  },
  {
    icon: require('@/assets/icons/payments/spotify 1.png'),
    keywords: ['스포티파이', 'spotify'],
  },
  {
    icon: require('@/assets/icons/payments/millie 1.png'),
    keywords: ['밀리의서재', '밀리서재', 'millie'],
  },
  {
    icon: require('@/assets/icons/payments/chatgpt 1.png'),
    keywords: ['chatgpt', '챗gpt', 'openai'],
  },
  {
    icon: require('@/assets/icons/payments/naver 1.png'),
    keywords: ['네이버', 'naver'],
  },
  {
    icon: require('@/assets/icons/payments/baemin 1.png'),
    keywords: ['배달의민족', '배민', 'baemin'],
  },
];

/** 짝이 없거나 애매한 서비스가 모두 쓰는 아이콘. 가계부 기타 카테고리와 같은 자산이다. */
const FALLBACK_SERVICE_ICON: ImageSourcePropType = require('@/assets/icons/category/etc_icon.png');

export function getSubscriptionServiceIcon(
  serviceName: string | null | undefined,
): ImageSourcePropType {
  if (!serviceName) return FALLBACK_SERVICE_ICON;

  const normalizedName = normalize(serviceName);
  if (!normalizedName) return FALLBACK_SERVICE_ICON;

  const matched = SERVICE_ICON_RULES.find((rule) =>
    rule.keywords.some((keyword) => normalizedName.includes(keyword)),
  );

  return matched?.icon ?? FALLBACK_SERVICE_ICON;
}
