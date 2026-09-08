import { StyleSheet } from 'react-native';

import { colors, componentSize, spacing } from '@/styles';

export const styles = StyleSheet.create({
  button: {
    position: 'absolute',
    top: spacing[12],
    left: spacing[3],
    zIndex: 1,
    width: componentSize.touchTarget,
    height: componentSize.touchTarget,
    alignItems: 'center',
    justifyContent: 'center',
  },
  icon: {
    color: colors.textPrimary,
    fontSize: 36,
    lineHeight: 36,
  },
});
