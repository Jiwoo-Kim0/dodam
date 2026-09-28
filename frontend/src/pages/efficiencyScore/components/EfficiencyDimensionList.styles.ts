import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  list: {
    marginTop: spacing[1],
  },
  row: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[3],
    paddingVertical: spacing[3],
  },
  rowDivider: {
    borderBottomColor: colors.divider,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  gauge: {
    alignItems: 'center',
    height: 52,
    justifyContent: 'center',
    position: 'relative',
    width: 52,
  },
  gaugeScore: {
    color: colors.textPrimary,
    fontFamily: fontFamily.bold,
    fontSize: fontSize.body2,
    lineHeight: 18,
    position: 'absolute',
  },
  gaugeScoreUnavailable: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
  },
  rowContent: {
    flex: 1,
    gap: spacing[1],
  },
  rowHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  rowLabel: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  rowStatus: {
    fontFamily: fontFamily.medium,
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
  rowDescription: {
    ...typography.caption,
    color: colors.textSecondary,
    lineHeight: 17,
  },
});
