import { StyleSheet } from 'react-native';

import { colors, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  optionList: {
    gap: spacing[2],
  },
  consentSection: {
    marginTop: spacing[8],
    gap: spacing[3],
  },
  errorText: {
    ...typography.body2,
    // 팔레트에 정의된 유일한 경고색이다.
    color: colors.ledger.expense,
    lineHeight: 18,
  },
});
