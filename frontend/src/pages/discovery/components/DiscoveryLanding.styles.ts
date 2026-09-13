import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, semanticSpacing, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  safeArea: {
    backgroundColor: colors.white,
  },
  scrollArea: {
    flex: 1,
  },
  scrollContent: {
    backgroundColor: colors.white,
  },
  section: {
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingBottom: spacing[6],
    paddingHorizontal: semanticSpacing.screenMargin,
    paddingTop: spacing[5],
  },
  heroSection: {
    backgroundColor: colors.white,
  },
  lastSection: {
    paddingBottom: spacing[6],
  },
  compactSection: {
    gap: spacing[3],
    justifyContent: 'flex-start',
  },
  topBar: {
    alignItems: 'center',
    alignSelf: 'stretch',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  backIcon: {
    color: colors.textPrimary,
    fontFamily: fontFamily.regular,
    fontSize: 34,
    lineHeight: 36,
  },
  heroCopy: {
    alignItems: 'center',
    gap: spacing[3],
  },
  heroTitle: {
    color: colors.textPrimary,
    lineHeight: 38,
    textAlign: 'center',
    ...typography.headline,
  },
  greenText: {
    color: colors.primary.green,
  },
  brownText: {
    color: colors.primary.brown,
  },
  description: {
    color: colors.textSecondary,
    lineHeight: 19,
    textAlign: 'center',
    ...typography.body2,
  },
  bubbleGroup: {
    alignSelf: 'stretch',
    gap: spacing[2],
    paddingHorizontal: spacing[2],
  },
  speechBubble: {
    backgroundColor: colors.white,
    borderRadius: radius.default,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
    shadowColor: colors.textPrimary,
    shadowOffset: { height: 2, width: 0 },
    shadowOpacity: 0.05,
    shadowRadius: 5,
    elevation: 1,
  },
  bubbleLeft: {
    alignSelf: 'flex-start',
  },
  bubbleRight: {
    alignSelf: 'flex-end',
  },
  bubbleText: {
    color: colors.textSecondary,
    ...typography.body2,
  },
  heroImage: {
    aspectRatio: 800 / 554,
    width: '100%',
  },
  scrollHint: {
    alignItems: 'center',
    gap: spacing[1],
  },
  scrollHintText: {
    color: colors.textTertiary,
    ...typography.caption,
  },
  scrollArrow: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.subtitle,
  },
  stageHeader: {
    alignItems: 'center',
    gap: spacing[2],
  },
  stageSkip: {
    alignSelf: 'flex-end',
  },
  stageSkipText: {
    color: colors.textTertiary,
    ...typography.caption,
  },
  stageLabel: {
    color: colors.textTertiary,
    ...typography.caption,
  },
  stageTitle: {
    color: colors.textPrimary,
    lineHeight: 36,
    textAlign: 'center',
    ...typography.headline,
  },
  stageDescription: {
    color: colors.textSecondary,
    lineHeight: 18,
    textAlign: 'center',
    ...typography.caption,
  },
  financeImage: {
    aspectRatio: 362 / 434,
    maxHeight: 430,
    width: '88%',
  },
  diagnosisImage: {
    aspectRatio: 400 / 344,
    width: '52%',
  },
  priorityImage: {
    aspectRatio: 610 / 440,
    width: '72%',
  },
  comparisonImage: {
    aspectRatio: 340 / 258,
    width: '44%',
  },
  noticeBanner: {
    alignItems: 'center',
    alignSelf: 'stretch',
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.default,
    flexDirection: 'row',
    gap: spacing[2],
    justifyContent: 'center',
    padding: spacing[3],
  },
  checkCircle: {
    alignItems: 'center',
    backgroundColor: colors.primary.green,
    borderRadius: radius.round,
    height: 20,
    justifyContent: 'center',
    width: 20,
  },
  checkMark: {
    color: colors.white,
    fontFamily: fontFamily.bold,
    fontSize: fontSize.caption,
  },
  noticeText: {
    color: colors.textSecondary,
    ...typography.body2,
  },
  cardList: {
    alignSelf: 'stretch',
    gap: spacing[3],
  },
  stageVisualGroup: {
    alignItems: 'center',
    alignSelf: 'stretch',
    gap: spacing[2],
  },
  insightCard: {
    alignItems: 'center',
    backgroundColor: colors.background,
    borderRadius: radius.default,
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: spacing[4],
    minHeight: 74,
    paddingVertical: spacing[2],
  },
  insightCopy: {
    flex: 1,
  },
  insightTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
    lineHeight: 19,
  },
  insightDescription: {
    color: colors.textTertiary,
    marginTop: spacing[1],
    ...typography.caption,
  },
  chevron: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.title,
  },
  rankingList: {
    alignSelf: 'stretch',
    gap: spacing[3],
  },
  rankingRow: {
    alignItems: 'center',
    backgroundColor: colors.white,
    borderColor: colors.divider,
    borderRadius: radius.default,
    borderWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    gap: spacing[2],
    minHeight: 76,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[2],
    shadowColor: colors.textPrimary,
    shadowOffset: { height: 1, width: 0 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
    elevation: 0,
  },
  rankCircle: {
    alignItems: 'center',
    backgroundColor: colors.primary.green,
    borderRadius: radius.round,
    height: 24,
    justifyContent: 'center',
    width: 24,
  },
  rankFirst: {
    backgroundColor: colors.primary.brown,
  },
  rankSecond: {
    backgroundColor: colors.primary.yellow,
  },
  rankText: {
    color: colors.white,
    fontFamily: fontFamily.bold,
    fontSize: fontSize.caption,
  },
  rankingCopy: {
    flex: 1,
  },
  rankingTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
  },
  rankingDescription: {
    color: colors.textTertiary,
    marginTop: spacing[1],
    ...typography.caption,
  },
  amountWrap: {
    alignItems: 'flex-end',
  },
  amountLabel: {
    color: colors.textTertiary,
    ...typography.caption,
  },
  amountText: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
    marginTop: spacing[1],
  },
  comparisonShowcase: {
    alignItems: 'center',
    alignSelf: 'stretch',
  },
  comparisonIntro: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'center',
    width: '100%',
  },
  changeBubble: {
    backgroundColor: colors.white,
    borderColor: colors.divider,
    borderRadius: radius.default,
    borderWidth: StyleSheet.hairlineWidth,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[2],
    shadowColor: colors.textPrimary,
    shadowOffset: { height: 2, width: 0 },
    shadowOpacity: 0.08,
    shadowRadius: 4,
  },
  changeBubbleText: {
    color: colors.textPrimary,
    lineHeight: 17,
    textAlign: 'center',
    ...typography.caption,
  },
  comparisonCards: {
    alignItems: 'flex-end',
    flexDirection: 'row',
    gap: spacing[4],
    justifyContent: 'center',
    marginTop: -spacing[3],
  },
  comparisonCard: {
    alignItems: 'center',
    backgroundColor: colors.white,
    borderRadius: radius.default,
    elevation: 3,
    padding: spacing[2],
    shadowColor: colors.textPrimary,
    shadowOffset: { height: 2, width: 0 },
    shadowOpacity: 0.14,
    shadowRadius: 5,
    width: 112,
  },
  highlightedComparisonCard: {
    backgroundColor: colors.primary.lightGreen,
    borderColor: colors.primary.green,
    borderWidth: StyleSheet.hairlineWidth,
    minHeight: 150,
  },
  comparisonCardTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
  },
  barChart: {
    alignItems: 'flex-end',
    flexDirection: 'row',
    gap: spacing[1],
    height: 48,
    marginTop: spacing[2],
  },
  chartBar: {
    backgroundColor: colors.divider,
    borderRadius: radius.small,
    width: 12,
  },
  highlightedBar: {
    backgroundColor: colors.primary.green,
  },
  shortBar: {
    height: 20,
  },
  mediumBar: {
    height: 32,
  },
  tallBar: {
    height: 46,
  },
  comparisonCardLabel: {
    color: colors.textTertiary,
    marginTop: spacing[2],
    ...typography.caption,
  },
  comparisonCardAmount: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
    marginTop: spacing[2],
  },
  resultList: {
    alignSelf: 'stretch',
    gap: spacing[2],
    marginHorizontal: spacing[2],
  },
  resultRow: {
    alignItems: 'center',
    backgroundColor: colors.background,
    borderRadius: radius.default,
    flexDirection: 'row',
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[2],
  },
  resultCopy: {
    flex: 1,
  },
  resultLabel: {
    color: colors.textSecondary,
    ...typography.body2,
  },
  resultValue: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
    marginTop: spacing[1],
  },
  startButton: {
    alignItems: 'center',
    alignSelf: 'stretch',
    backgroundColor: colors.primary.green,
    borderRadius: radius.default,
    justifyContent: 'center',
    minHeight: 52,
  },
  startButtonText: {
    color: colors.white,
    ...typography.buttonSmall,
  },
});
