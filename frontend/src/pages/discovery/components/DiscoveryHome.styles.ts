import { StyleSheet } from 'react-native';

import {
  fontSize,
  colors,
  componentSize,
  fontFamily,
  radius,
  semanticSpacing,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  screen: {
    backgroundColor: colors.background,
  },
  scroll: {
    backgroundColor: colors.home.goalGradientEnd,
  },
  /**
   * 아래 여백은 반드시 여기에 준다. 스크롤 컨텐츠에 주면 그라데이션 바깥이라
   * 흰 띠(`scrollContent`의 배경)가 그대로 드러난다.
   */
  gradientContainer: {
    marginTop: spacing[8],
    paddingBottom: spacing[12],
    position: 'relative',
  },
  gradientOverlay: {
    left: -semanticSpacing.screenMargin,
    position: 'absolute',
    right: -semanticSpacing.screenMargin,
    top: 190,
  },
  scrollContent: {
    backgroundColor: colors.background,
    paddingHorizontal: semanticSpacing.screenMargin,
  },
  titleGroup: {
    gap: spacing[2],
    marginTop: spacing[2],
  },
  subtitle: {
    ...typography.title,
    color: colors.textPrimary,
  },
  subtitleHighlight: {
    color: colors.primary.green,
  },
  summaryShowcase: {
    alignItems: 'center',
    height: 252,
    justifyContent: 'flex-end',
    marginHorizontal: -semanticSpacing.screenMargin,
    position: 'relative',
  },
  summaryCard: {
    alignItems: 'center',
    backgroundColor: colors.white,
    borderRadius: radius.card,
    elevation: 3,
    height: 156,
    justifyContent: 'center',
    paddingHorizontal: spacing[2],
    shadowColor: colors.textPrimary,
    shadowOffset: { height: 3, width: 0 },
    shadowOpacity: 0.2,
    shadowRadius: 4,
    width: 228,
    zIndex: 2,
  },
  summaryIcon: {
    height: 80,
    width: 80,
  },
  summaryText: {
    ...typography.body1,
    color: colors.textPrimary,
    marginTop: 2,
    textAlign: 'center',
  },
  summaryNumber: {
    fontFamily: fontFamily.bold,
  },
  summaryAmountHighlight: {
    backgroundColor: colors.primary.lightGreen,
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.button,
  },
  summarySubtext: {
    ...typography.body2,
    color: colors.textSecondary,
    marginTop: spacing[3],
    textAlign: 'center',
  },
  summarySubtextNumber: {
    backgroundColor: colors.primary.lightGreen,
    color: colors.textSecondary,
    fontFamily: fontFamily.bold,
  },
  summaryDaysHighlight: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
  },
  illustrationWrap: {
    aspectRatio: 185 / 124,
    position: 'absolute',
    top: 0,
    width: 185,
    zIndex: 3,
  },
  squirrelImage: {
    height: '100%',
    width: '100%',
  },
  glassSticker: {
    aspectRatio: 88 / 112,
    position: 'absolute',
    right: -15,
    top: -6,
    width: 81,
  },
  sideSummaryCard: {
    alignItems: 'center',
    backgroundColor: colors.white,
    borderRadius: radius.card,
    elevation: 2,
    height: 108,
    justifyContent: 'center',
    position: 'absolute',
    shadowColor: colors.textPrimary,
    shadowOffset: { height: 2, width: 0 },
    shadowOpacity: 0.16,
    shadowRadius: 3,
    bottom: spacing[5],
    width: 128,
  },
  leftSummaryCard: {
    left: -52,
  },
  rightSummaryCard: {
    right: -52,
  },
  sideSummaryImage: {
    height: 100,
    width: 100,
  },
  insightSection: {
    gap: spacing[3],
    marginTop: spacing[8],
    minHeight: 270,
  },
  sectionHeaderRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  /** 리포트 화면과 같은 카드 제목 규칙이다. 제목은 작고 연하게, 값이 눈에 먼저 들어오게 한다. */
  sectionTitle: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  diagnosisAction: {
    alignItems: 'center',
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.default,
    justifyContent: 'center',
    minHeight: 44,
    paddingHorizontal: spacing[3],
  },
  diagnosisActionDisabled: {
    backgroundColor: colors.ledger.softSurface,
  },
  diagnosisActionPressed: {
    opacity: 0.7,
  },
  diagnosisActionText: {
    color: colors.primary.green,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
  },
  diagnosisActionTextDisabled: {
    color: colors.textTertiary,
  },
  diagnosisFooter: {
    marginTop: spacing[6],
  },
  insightListCard: {
    gap: spacing[2],
  },
  /** 아래 안내 문구(`cooldownLabel`)와 같은 축에 두려고 가운데 정렬한다. */
  footnote: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 14,
    textAlign: 'center',
  },
  viewAllButton: {
    alignItems: 'center',
    backgroundColor: colors.primary.green,
    borderRadius: radius.default,
    height: componentSize.button.default,
    justifyContent: 'center',
    marginTop: spacing[8],
  },
  viewAllButtonPressed: {
    opacity: 0.8,
  },
  viewAllButtonDisabled: {
    opacity: 0.45,
  },
  cooldownLabel: {
    ...typography.caption,
    color: colors.textTertiary,
    marginBottom: spacing[4],
    textAlign: 'center',
  },
  viewAllText: {
    ...typography.body2,
    color: colors.primary.green,
    fontFamily: fontFamily.medium,
  },
  viewAllButtonLabel: {
    ...typography.button,
    color: colors.white,
  },
});
