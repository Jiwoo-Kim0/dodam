import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, semanticSpacing, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  scoreCard: {
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  scoreCardPressed: {
    opacity: 0.85,
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
  difference: {
    ...typography.caption,
    color: colors.textSecondary,
    marginTop: spacing[1],
  },
  summary: {
    ...typography.body2,
    color: colors.textPrimary,
    lineHeight: 18,
    marginTop: spacing[2],
  },
  moreRow: {
    alignItems: 'center',
    borderTopColor: colors.divider,
    borderTopWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: spacing[3],
    paddingTop: spacing[3],
  },
  moreText: {
    ...typography.caption,
    color: colors.report.scoreStrong,
  },
  moreChevron: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body1,
    lineHeight: 16,
  },
  emptyMessage: {
    ...typography.body2,
    color: colors.textSecondary,
    lineHeight: 19,
    marginTop: semanticSpacing.cardTitleContentGap,
  },
});
