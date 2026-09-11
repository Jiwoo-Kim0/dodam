import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing } from '@/styles';

export const styles = StyleSheet.create({
  summaryCard: {
    alignItems: 'center',
    flexDirection: 'row',
    minHeight: 144,
    overflow: 'hidden',
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[5],
  },
  summaryCharacterFrame: {
    height: 104,
    marginLeft: -14,
    marginRight: spacing[2],
    position: 'relative',
    width: 104,
  },
  summaryCharacter: {
    height: '100%',
    width: '100%',
  },
  summaryCharacterCornerMask: {
    backgroundColor: colors.briefing.card,
    bottom: 0,
    height: 12,
    position: 'absolute',
    right: 0,
    width: 12,
  },
  summaryContent: {
    flex: 1,
    minWidth: 0,
  },
  summaryHeadingRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  summaryLabel: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body1,
    lineHeight: 20,
  },
  statusBadge: {
    backgroundColor: colors.briefing.statusBackground,
    borderColor: colors.primary.green,
    borderRadius: radius.round,
    borderWidth: 1,
    paddingHorizontal: spacing[3],
    paddingVertical: 5,
  },
  statusBadgeText: {
    color: colors.briefing.statusText,
    fontFamily: fontFamily.semiBold,
    fontSize: 11,
    lineHeight: 15,
  },
  summaryAmount: {
    color: colors.primary.brown,
    fontFamily: fontFamily.bold,
    fontSize: 30,
    letterSpacing: -0.6,
    lineHeight: 38,
    marginTop: spacing[2],
  },
  summaryAmountUnit: {
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body1,
  },
  averageText: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: 11,
    lineHeight: 16,
  },
  progressTrack: {
    backgroundColor: colors.briefing.progressTrack,
    borderRadius: radius.round,
    height: 7,
    marginTop: spacing[2],
    overflow: 'hidden',
  },
  progressValue: {
    backgroundColor: colors.primary.brown,
    borderRadius: radius.round,
    height: '100%',
  },
});
