import { StyleSheet } from 'react-native';

import {
  colors,
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
    backgroundColor: colors.white,
    borderRadius: radius.card,
    elevation: 1,
    gap: spacing[2],
    padding: spacing[4],
    shadowColor: colors.textPrimary,
    shadowOffset: { height: 2, width: 0 },
    shadowOpacity: 0.08,
    shadowRadius: 3,
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
    fontSize: 12,
  },
  summaryCount: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: 13,
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
    fontSize: 12,
  },
  summaryPillValue: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: 13,
  },
  listSection: {
    gap: spacing[2],
    marginTop: spacing[6],
  },
  sectionTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: 14,
    lineHeight: 19,
  },
});
