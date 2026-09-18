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
  /** 유형명을 주인공으로 두는 첫 카드다. 아래 카드보다 위아래 여백을 넉넉히 준다. */
  heroCard: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[3],
    paddingVertical: spacing[4],
  },
  heroContent: {
    alignItems: 'flex-start',
    flex: 1,
    minWidth: 0,
  },
  heroCharacter: {
    height: 68,
    width: 68,
  },
  heroNotes: {
    marginTop: spacing[2],
  },
  badge: {
    backgroundColor: colors.report.patternSurface,
    borderRadius: radius.round,
    paddingHorizontal: spacing[2],
    paddingVertical: 3,
  },
  badgeText: {
    color: colors.report.scoreStrong,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.micro,
    lineHeight: 14,
  },
  clusterLabel: {
    ...typography.headline,
    color: colors.textPrimary,
    letterSpacing: -0.8,
    lineHeight: 34,
    marginTop: spacing[2],
  },
  groupLabel: {
    ...typography.body2,
    color: colors.textSecondary,
    marginTop: spacing[1],
  },
  /** 표본 수가 분석 근거라 이 문장에서 가장 눈에 띄어야 한다. */
  groupSampleSize: {
    color: colors.textPrimary,
    fontFamily: fontFamily.bold,
  },
  note: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 16,
  },
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  /** 리포트 카드 제목과 같은 규칙이다. */
  cardTitle: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  /** 제목과 붙어 보이지 않도록 리포트 카드의 제목·콘텐츠 간격을 그대로 쓴다. */
  cardDescription: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 17,
    marginTop: semanticSpacing.cardTitleContentGap,
  },
  bars: {
    marginTop: semanticSpacing.cardTitleContentGap,
  },
  legend: {
    flexDirection: 'row',
    gap: spacing[4],
    justifyContent: 'center',
    marginTop: spacing[3],
  },
  legendItem: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[1],
  },
  legendBandMark: {
    backgroundColor: colors.report.chartBar,
    borderRadius: radius.round,
    height: 6,
    width: 14,
  },
  legendDotMark: {
    backgroundColor: colors.primary.green,
    borderColor: colors.white,
    borderRadius: 6,
    borderWidth: 2,
    height: 12,
    width: 12,
  },
  legendText: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 14,
  },
  insightList: {
    gap: spacing[2],
    marginTop: semanticSpacing.cardTitleContentGap,
  },
  /** 문장 하나하나를 옅은 블록으로 떼어 놓아 목록이 눈에 들어오게 한다. */
  insightRow: {
    backgroundColor: colors.report.patternSurface,
    borderRadius: radius.default,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[3],
  },
  insightText: {
    ...typography.body2,
    color: colors.textPrimary,
    lineHeight: 20,
  },
});
