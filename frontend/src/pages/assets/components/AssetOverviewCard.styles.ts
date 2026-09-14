import { StyleSheet } from 'react-native';

import {
  colors,
  fontFamily,
  radius,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  card: {
    padding: spacing[4],
    borderRadius: radius.card,
    backgroundColor: colors.white,
  },
  label: {
    ...typography.caption,
    marginBottom: spacing[1],
    color: colors.textSecondary,
  },
  totalAmount: {
    ...typography.display,
    color: colors.textPrimary,
  },
  currencyUnit: {
    ...typography.body1,
    fontFamily: fontFamily.regular,
  },
  changeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: spacing[1],
  },
  change: {
    ...typography.caption,
    color: colors.primary.green,
  },
  changeDescription: {
    ...typography.caption,
    color: colors.textTertiary,
  },
  divider: {
    height: StyleSheet.hairlineWidth,
    marginVertical: spacing[3],
    backgroundColor: colors.divider,
  },
  composition: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[4],
  },
  legend: {
    flex: 1,
    gap: spacing[1],
  },
  legendRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: spacing[3],
  },
  legendLabelGroup: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[2],
  },
  legendDot: {
    width: 7,
    height: 7,
    borderRadius: radius.round,
  },
  legendLabel: {
    ...typography.body2,
    color: colors.textSecondary,
  },
  legendAmount: {
    ...typography.body2,
    fontFamily: fontFamily.medium,
    color: colors.textPrimary,
    textAlign: 'right',
  },
});
