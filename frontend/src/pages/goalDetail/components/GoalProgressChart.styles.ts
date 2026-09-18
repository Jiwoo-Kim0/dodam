import { StyleSheet } from 'react-native';

import { colors, fontSize, radius, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  card: {
    marginTop: spacing[3],
    paddingHorizontal: spacing[4],
    paddingTop: spacing[4],
    paddingBottom: spacing[3],
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
    marginTop: spacing[2],
  },
  legend: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: spacing[2],
    paddingRight: spacing[1],
  },
  legendItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
  },
  legendDot: {
    width: 6,
    height: 6,
    borderRadius: radius.round,
  },
  legendBar: {
    width: 10,
    height: 6,
    borderRadius: radius.small,
  },
  legendLabel: {
    fontSize: fontSize.micro,
    lineHeight: 14,
    color: colors.textSecondary,
  },
});
