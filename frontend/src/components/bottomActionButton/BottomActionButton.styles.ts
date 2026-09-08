import { StyleSheet } from 'react-native';

import {
  colors,
  componentSize,
  radius,
  semanticSpacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  button: {
    width: '100%',
    height: componentSize.button.default,
    marginTop: 'auto',
    marginBottom: semanticSpacing.bottomActionOffset,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.default,
    backgroundColor: colors.primary.green,
  },
  label: {
    ...typography.button,
    color: colors.white,
  },
  disabled: {
    backgroundColor: colors.disabledBackground,
  },
  pressed: {
    opacity: 0.8,
  },
});
