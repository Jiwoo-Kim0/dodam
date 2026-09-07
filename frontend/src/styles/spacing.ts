/** 도담 디자인 가이드의 4px 기반 간격 토큰 */
export const spacing = {
  1: 4,
  2: 8,
  3: 12,
  4: 16,
  6: 24,
  8: 32,
} as const;

/** 사용 목적이 정해진 의미 기반 간격 */
export const semanticSpacing = {
  innerGap: spacing[2],
  componentGap: spacing[4],
  sectionGap: spacing[6],
  screenMargin: 20,
} as const;
