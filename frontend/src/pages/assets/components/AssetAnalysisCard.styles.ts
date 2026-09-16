import { StyleSheet } from 'react-native';

import { colors, fontFamily, radius, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    marginTop: spacing[4],
    padding: spacing[4],
  },
  title: {
    ...typography.subtitle,
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    marginBottom: spacing[3],
  },
  row: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[3],
    justifyContent: 'space-between',
  },
  labelGroup: {
    flex: 1,
    gap: spacing[1],
  },
  label: {
    ...typography.body2,
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
  },
  description: {
    ...typography.caption,
    color: colors.textSecondary,
  },
  amount: {
    ...typography.body2,
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    textAlign: 'right',
  },
  divider: {
    backgroundColor: colors.divider,
    height: StyleSheet.hairlineWidth,
    marginVertical: spacing[3],
  },
});
