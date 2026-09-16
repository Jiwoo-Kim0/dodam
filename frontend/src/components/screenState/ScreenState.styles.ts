import { StyleSheet } from 'react-native';

import {
  colors,
  componentSize,
  radius,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    gap: spacing[3],
    justifyContent: 'center',
    minHeight: 360,
    paddingHorizontal: spacing[6],
  },
  title: {
    ...typography.subtitle,
    color: colors.textPrimary,
    textAlign: 'center',
  },
  description: {
    ...typography.body1,
    color: colors.textSecondary,
    lineHeight: 21,
    textAlign: 'center',
  },
  retryButton: {
    alignItems: 'center',
    borderColor: colors.primary.green,
    borderRadius: radius.default,
    borderWidth: 1,
    justifyContent: 'center',
    minHeight: componentSize.button.compact,
    paddingHorizontal: spacing[5],
  },
  retryLabel: {
    ...typography.buttonSmall,
    color: colors.primary.green,
  },
  pressed: {
    opacity: componentSize.bottomSheet.pressedOpacity,
  },
});
