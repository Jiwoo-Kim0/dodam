import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing, typography } from '@/styles';

const TRACK_HEIGHT = 6;

export const styles = StyleSheet.create({
  list: {
    gap: spacing[4],
  },
  row: {
    gap: spacing[2],
  },
  rowHeader: {
    alignItems: 'baseline',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  rowLabel: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  rowValueGroup: {
    alignItems: 'baseline',
    flexDirection: 'row',
    gap: spacing[2],
  },
  rowStatus: {
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 16,
  },
  statusGood: {
    color: colors.report.scoreStrong,
  },
  statusNormal: {
    color: colors.textSecondary,
  },
  statusCheck: {
    color: colors.primary.orange,
  },
  statusUnavailable: {
    color: colors.textTertiary,
  },
  rowScore: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  rowScoreUnavailable: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
  },
  track: {
    backgroundColor: colors.background,
    borderRadius: radius.round,
    height: TRACK_HEIGHT,
    overflow: 'hidden',
  },
  fill: {
    borderRadius: radius.round,
    height: TRACK_HEIGHT,
  },
  fillGood: {
    backgroundColor: colors.primary.green,
  },
  fillNormal: {
    backgroundColor: colors.report.scoreLight,
  },
  fillCheck: {
    backgroundColor: colors.primary.orange,
  },
  fillUnavailable: {
    backgroundColor: colors.divider,
  },
  rowDescription: {
    ...typography.caption,
    color: colors.textSecondary,
    lineHeight: 17,
  },
});
