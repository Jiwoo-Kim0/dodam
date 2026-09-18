import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    gap: spacing[1],
    marginTop: spacing[6],
    paddingHorizontal: spacing[5],
    paddingVertical: spacing[5],
  },
  categoryLabel: {
    ...typography.body1,
    color: colors.textSecondary,
  },
  amountRow: {
    alignItems: 'baseline',
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing[2],
    justifyContent: 'space-between',
  },
  amount: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: 28,
    lineHeight: 36,
  },
  changePercent: {
    color: colors.report.scoreStrong,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
  },
  bars: {
    gap: spacing[3],
    marginTop: spacing[3],
  },
  barRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[3],
  },
  barLabel: {
    ...typography.body2,
    color: colors.textPrimary,
    width: 58,
  },
  barTrack: {
    backgroundColor: colors.home.goalProgressTrack,
    borderRadius: radius.round,
    flex: 1,
    height: 10,
    overflow: 'hidden',
  },
  previousBar: {
    backgroundColor: colors.report.scoreLight,
    borderRadius: radius.round,
    height: '100%',
  },
  recentBar: {
    backgroundColor: colors.primary.green,
    borderRadius: radius.round,
    height: '100%',
  },
  barAmount: {
    ...typography.body1,
    color: colors.textPrimary,
    minWidth: 78,
    textAlign: 'right',
  },
});
