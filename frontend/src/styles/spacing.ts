export const spacing = {
  1: 4,
  2: 8,
  3: 12,
  4: 16,
  5: 20,
  6: 24,
  8: 32,
  12: 48,
  20: 80,
} as const;

export const semanticSpacing = {
  innerGap: spacing[2],
  componentGap: spacing[4],
  sectionGap: spacing[6],
  screenMargin: spacing[5],
  screenTitleTop: spacing[20],
  screenTitleContentGap: spacing[12],
} as const;