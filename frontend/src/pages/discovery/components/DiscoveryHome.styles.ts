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
  gradientContainer: {
    marginTop: spacing[8],
    paddingBottom: spacing[8],
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
  summaryAmountHighlight: {
    backgroundColor: colors.primary.lightGreen,
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.button,
  },
  summarySubtext: {
    ...typography.body2,
    color: colors.textSecondary,
    marginTop: spacing[2],
    textAlign: 'center',
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
    gap: spacing[4],
    marginTop: spacing[8],
  },
  sectionTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: 16,
    lineHeight: 22,
  },
  insightListCard: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    overflow: 'hidden',
  },
  footnote: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 14,
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
  viewAllButtonLabel: {
    ...typography.button,
    color: colors.white,
  },
});
