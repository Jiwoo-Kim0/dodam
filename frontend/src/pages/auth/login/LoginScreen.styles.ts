import { StyleSheet } from 'react-native';

import { colors, radius, spacing, typography } from '@/styles';

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
  // 시연용 진입점이라 소셜 버튼보다 눈에 덜 띄게 둔다. .env에 시연 계정이 있을 때만 보인다.
  demoButton: {
    marginTop: spacing[2],
    paddingVertical: spacing[3],
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.default,
    borderWidth: 1,
    borderColor: colors.divider,
    backgroundColor: colors.white,
  },
  demoButtonPressed: {
    opacity: 0.8,
  },
  demoLabel: {
    ...typography.buttonSmall,
    color: colors.textSecondary,
  },
});
