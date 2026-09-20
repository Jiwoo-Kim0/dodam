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
    paddingBottom: spacing[8],
    paddingHorizontal: semanticSpacing.screenMargin,
  },
  titleSection: {
    marginTop: spacing[4],
  },
  categoryBadge: {
    alignSelf: 'flex-start',
    backgroundColor: colors.goal.savingCharacterBackground,
    borderRadius: radius.round,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[1],
  },
  categoryBadgeText: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
  },
  /** 화면 머리글이다. 26·30px은 본문 대비 너무 커서 한 단계씩 낮췄다. */
  title: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.title,
    letterSpacing: -0.4,
    lineHeight: 30,
    marginTop: spacing[4],
  },
  headlineTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.title,
    letterSpacing: -0.4,
    lineHeight: 30,
    marginTop: spacing[5],
  },
  headlineAmount: {
    color: colors.report.scoreStrong,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.headline,
    letterSpacing: -0.6,
    lineHeight: 34,
    marginTop: spacing[1],
  },
  headlineDescription: {
    ...typography.body2,
    color: colors.textSecondary,
    lineHeight: 20,
    marginTop: spacing[3],
  },
  adviceSection: {
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.card,
    gap: spacing[2],
    marginTop: spacing[6],
    padding: spacing[4],
  },
  adviceHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[3],
  },
  adviceImage: {
    height: 64,
    width: 64,
  },
  impactCard: {
    alignItems: 'center',
    backgroundColor: colors.report.patternSurface,
    borderRadius: radius.card,
    flexDirection: 'row',
    gap: spacing[3],
    marginTop: spacing[3],
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  impactIconWrap: {
    alignItems: 'center',
    backgroundColor: colors.report.forecastLiving,
    borderRadius: radius.round,
    height: 36,
    justifyContent: 'center',
    width: 36,
  },
  impactIcon: {
    height: 20,
    width: 20,
  },
  impactText: {
    ...typography.body2,
    color: colors.report.scoreStrong,
    flex: 1,
    lineHeight: 19,
  },
  impactEmphasis: {
    color: colors.report.scoreStrong,
    fontFamily: fontFamily.semiBold,
  },
  adviceHeaderCopy: {
    flex: 1,
    gap: spacing[1],
  },
  adviceEyebrow: {
    ...typography.caption,
    color: colors.primary.green,
  },
  adviceHeading: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
  },
  adviceText: {
    ...typography.body1,
    color: colors.textPrimary,
    lineHeight: 22,
  },
  coachSummary: {
    borderColor: colors.divider,
    borderTopWidth: StyleSheet.hairlineWidth,
    gap: spacing[2],
    marginTop: spacing[2],
    paddingTop: spacing[4],
  },
  coachSection: {
    backgroundColor: colors.report.patternSurface,
    borderRadius: radius.card,
    gap: spacing[2],
    marginTop: spacing[6],
    padding: spacing[4],
  },
  coachLabel: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
  },
  recommendationSection: {
    marginTop: spacing[6],
  },
  /** 본문 대비 18px은 너무 커서 한 단계 낮춘다. 카테고리 소비 섹션 제목과 같은 크기다. */
  sectionTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.buttonSmall,
    lineHeight: 21,
  },
  sectionDescription: {
    ...typography.body2,
    color: colors.textSecondary,
    lineHeight: 19,
    marginTop: spacing[2],
  },
  recommendationList: {
    gap: spacing[2],
    marginTop: spacing[4],
  },
  recommendationCard: {
    backgroundColor: colors.white,
    borderColor: colors.divider,
    borderRadius: radius.card,
    borderWidth: StyleSheet.hairlineWidth,
    gap: spacing[3],
    padding: spacing[4],
  },
  recommendedCard: {
    borderColor: colors.primary.green,
    borderWidth: 1,
  },
  recommendationHeader: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    gap: spacing[2],
    justifyContent: 'space-between',
  },
  recommendationExplanation: {
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.card,
    marginTop: spacing[2],
    padding: spacing[4],
  },
  recommendationTitle: {
    color: colors.textPrimary,
    flex: 1,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
    lineHeight: 21,
  },
  recommendationDetail: {
    ...typography.body2,
    color: colors.textSecondary,
    lineHeight: 20,
  },
  inlineRetry: {
    ...typography.buttonSmall,
    color: colors.primary.green,
    marginTop: spacing[2],
  },
  recommendedBadge: {
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.round,
    paddingHorizontal: spacing[2],
    paddingVertical: spacing[1],
  },
  recommendedBadgeText: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.caption,
  },
  savingRow: {
    alignItems: 'center',
    borderColor: colors.divider,
    borderTopWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingTop: spacing[3],
  },
  savingLabel: {
    ...typography.body2,
    color: colors.textSecondary,
  },
  savingValue: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
  },
  /** 아직 값이 없는 상태다. 초록으로 두면 계산된 절감액처럼 읽힌다. */
  savingValuePending: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
  },
  recommendationAction: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[1],
    justifyContent: 'flex-end',
  },
  recommendationActionText: {
    ...typography.buttonSmall,
    color: colors.primary.green,
  },
  recommendationActionIcon: {
    ...typography.buttonSmall,
    color: colors.primary.green,
  },
  spendingSummaryLabel: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
  },
  spendingSummaryText: {
    ...typography.body1,
    color: colors.textPrimary,
    lineHeight: 24,
  },
  spendingSummaryAmount: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.button,
  },
  emptyAction: {
    ...typography.body1,
    color: colors.primary.green,
    marginTop: spacing[5],
  },
});
