import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, semanticSpacing, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  scoreCard: {
    alignItems: 'center',
    flexDirection: 'row',
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  scoreContent: {
    flex: 1,
    minWidth: 0,
  },
  scoreRow: {
    alignItems: 'baseline',
    flexDirection: 'row',
    marginTop: semanticSpacing.cardTitleContentGap,
  },
  /** 가계부의 대표 금액과 같은 display(34) 규칙을 쓴다. */
  score: {
    ...typography.display,
    color: colors.textPrimary,
    letterSpacing: -1,
    lineHeight: 40,
  },
  scoreDivider: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.headline,
    lineHeight: 38,
    marginLeft: 2,
  },
  maxScore: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.subtitle,
    lineHeight: 30,
    marginLeft: spacing[2],
  },
  differenceBadge: {
    alignItems: 'center',
    alignSelf: 'flex-start',
    backgroundColor: colors.background,
    borderRadius: radius.round,
    flexDirection: 'row',
    gap: spacing[1],
    marginTop: spacing[2],
    paddingHorizontal: spacing[2],
    paddingVertical: spacing[1],
  },
  differenceLabel: {
    ...typography.caption,
    color: colors.primary.green,
    lineHeight: 17,
  },
  differenceValue: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.caption,
    lineHeight: 17,
  },
  summary: {
    ...typography.body2,
    color: colors.textPrimary,
    lineHeight: 18,
    marginTop: spacing[1],
  },
});
