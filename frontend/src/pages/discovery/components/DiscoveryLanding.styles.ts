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
    gap: spacing[2],
    justifyContent: 'space-between',
    paddingBottom: spacing[3],
    paddingTop: spacing[3],
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
    position: 'relative',
  },
  /** 공통 헤더의 뒤로 가기와 같은 44px 터치 영역을 쓰되, 아이콘은 왼쪽 끝에 맞춘다. */
  backButton: {
    alignItems: 'flex-start',
    height: 44,
    justifyContent: 'center',
    width: 44,
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
  financeVisual: {
    aspectRatio: 362 / 434,
    maxWidth: 358,
    position: 'relative',
    width: '88%',
  },
  financeImage: {
    height: '100%',
    width: '100%',
  },
  financePhoneScreen: {
    gap: spacing[4],
    height: '65%',
    justifyContent: 'center',
    left: '19.5%',
    position: 'absolute',
    top: '17%',
    width: '46%',
  },
  financeLogoRow: {
    height: 44,
    overflow: 'hidden',
    width: '100%',
  },
  financeLogoTrack: {
    flexDirection: 'row',
    width: 448,
  },
  financeLogoGroup: {
    flexDirection: 'row',
    width: 224,
  },
  financeLogoSlot: {
    alignItems: 'center',
    height: 44,
    justifyContent: 'center',
    width: 56,
  },
  financeLogoImage: {
    height: 36,
    width: 36,
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
    width: '40%',
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
    gap: spacing[3],
    justifyContent: 'space-between',
    paddingHorizontal: spacing[4],
    minHeight: 74,
    paddingVertical: spacing[2],
  },
  insightCopy: {
    flex: 1,
    minWidth: 0,
  },
  insightIcon: {
    flexShrink: 0,
    height: 36,
    width: 36,
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
    paddingHorizontal: spacing[2],
    paddingVertical: spacing[1],
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
    gap: spacing[3],
    justifyContent: 'center',
    marginTop: -spacing[4],
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
    width: 104,
  },
  highlightedComparisonCard: {
    backgroundColor: colors.primary.lightGreen,
    borderColor: colors.primary.green,
    borderWidth: StyleSheet.hairlineWidth,
    minHeight: 132,
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
    height: 40,
    marginTop: spacing[1],
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
    height: 16,
  },
  mediumBar: {
    height: 28,
  },
  tallBar: {
    height: 38,
  },
  comparisonCardLabel: {
    color: colors.textTertiary,
    marginTop: spacing[1],
    ...typography.caption,
  },
  comparisonCardAmount: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
    marginTop: spacing[1],
  },
  resultList: {
    alignSelf: 'stretch',
    gap: spacing[1],
    marginHorizontal: spacing[2],
  },
  resultRow: {
    alignItems: 'center',
    backgroundColor: colors.background,
    borderRadius: radius.default,
    flexDirection: 'row',
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[1],
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
    minHeight: 48,
  },
  startButtonText: {
    color: colors.white,
    ...typography.buttonSmall,
  },
});
