import { StyleSheet } from 'react-native';

import { colors, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  buttonGroup: {
    gap: spacing[2],
  },
  errorText: {
    ...typography.body2,
    color: colors.ledger.expense,
    lineHeight: 18,
    marginTop: spacing[2],
    textAlign: 'center',
  },
});
