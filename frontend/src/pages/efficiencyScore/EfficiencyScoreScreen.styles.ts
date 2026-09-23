import { StyleSheet } from 'react-native';

import {
  colors,
  fontFamily,
  fontSize,
  radius,
  semanticSpacing,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  screen: {
    backgroundColor: colors.background,
  },
  scrollContent: {
    gap: spacing[3],
    paddingBottom: spacing[8],
    paddingHorizontal: semanticSpacing.screenMargin,
    paddingTop: spacing[2],
  },
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  /** 전체 점수를 주인공으로 두는 첫 카드다. */
  heroCard: {
    paddingVertical: spacing[4],
  },
  /** 점수 위에 작게 붙는 기준 달. 주인공인 점수보다 물러나 있어야 한다. */
  periodLabel: {
    ...typography.caption,
    color: colors.textSecondary,
    marginBottom: spacing[1],
  },
  scoreRow: {
    alignItems: 'baseline',
    flexDirection: 'row',
  },
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
    lineHeight: 19,
    marginTop: spacing[2],
  },
  /** 리포트 카드 제목과 같은 규칙이다. */
  cardTitle: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  scoreGuideText: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 17,
    marginTop: spacing[1],
  },
  dimensions: {
    marginTop: spacing[1],
  },
});
