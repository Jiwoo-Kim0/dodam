import { StyleSheet } from 'react-native';

import { colors, radius, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  card: {
    marginTop: spacing[5],
    paddingHorizontal: spacing[4],
    paddingTop: spacing[5],
    paddingBottom: spacing[4],
    borderRadius: radius.card,
    backgroundColor: colors.white,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: spacing[1],
  },
  title: {
    ...typography.body1,
    color: colors.textPrimary,
  },
  unit: {
    ...typography.caption,
    color: colors.textTertiary,
  },
  chart: {
    marginTop: spacing[3],
  },
  legend: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: spacing[4],
    paddingRight: spacing[2],
  },
  legendItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[1],
  },
  legendDot: {
    width: spacing[2],
    height: spacing[2],
    borderRadius: radius.round,
  },
  legendBar: {
    width: spacing[3],
    height: spacing[2],
    borderRadius: radius.small,
  },
  legendLabel: {
    ...typography.caption,
    color: colors.textSecondary,
  },
});
