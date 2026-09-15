import { StyleSheet } from 'react-native';

import {
  colors,
  componentSize,
  radius,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  buttonGroup: {
    gap: spacing[2],
  },
  demoSection: {
    borderTopColor: colors.divider,
    borderTopWidth: StyleSheet.hairlineWidth,
    gap: spacing[2],
    marginTop: spacing[3],
    paddingTop: spacing[4],
  },
  demoTitle: {
    ...typography.body2,
    color: colors.textSecondary,
  },
  demoInput: {
    ...typography.body1,
    backgroundColor: colors.ledger.softSurface,
    borderRadius: radius.default,
    color: colors.textPrimary,
    minHeight: componentSize.button.compact,
    paddingHorizontal: spacing[4],
  },
  demoButton: {
    alignItems: 'center',
    backgroundColor: colors.primary.green,
    borderRadius: radius.default,
    height: componentSize.button.compact,
    justifyContent: 'center',
  },
  demoButtonPressed: {
    opacity: 0.82,
  },
  demoButtonDisabled: {
    backgroundColor: colors.disabledBackground,
  },
  demoButtonLabel: {
    ...typography.buttonSmall,
    color: colors.white,
  },
  errorText: {
    ...typography.body2,
    color: colors.ledger.expense,
    lineHeight: 18,
    marginTop: spacing[2],
    textAlign: 'center',
  },
});
