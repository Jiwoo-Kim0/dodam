import { StyleSheet } from 'react-native';

import {
  fontSize,
  colors,
  fontFamily,
  radius,
  semanticSpacing,
  shadows,
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
  scrollContent: {
    backgroundColor: colors.background,
    paddingHorizontal: semanticSpacing.screenMargin,
  },
  gradientContainer: {
    marginTop: spacing[6],
    paddingBottom: spacing[4],
    position: 'relative',
  },
  gradientOverlay: {
    left: -semanticSpacing.screenMargin,
    position: 'absolute',
    right: -semanticSpacing.screenMargin,
  },
  summaryCard: {
    ...shadows.softCard,
    backgroundColor: colors.white,
    borderRadius: radius.card,
    gap: spacing[2],
    padding: spacing[4],
    zIndex: 2,
  },
  summaryHeaderRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  summaryLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
  },
  summaryCount: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
  },
  summaryHeadline: {
    ...typography.title,
    color: colors.textPrimary,
    lineHeight: 28,
  },
  summaryAmountHighlight: {
    color: colors.primary.green,
  },
  summaryPill: {
    alignItems: 'center',
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.round,
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[2],
  },
  summaryPillLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
  },
  summaryPillValue: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
  },
  listSection: {
    gap: spacing[2],
    marginTop: spacing[6],
  },
  sectionTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body1,
    lineHeight: 19,
  },
});
