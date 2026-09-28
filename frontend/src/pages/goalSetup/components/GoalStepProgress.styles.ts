import { StyleSheet } from 'react-native';

import { colors, componentSize, radius, spacing } from '@/styles';

export const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    top: spacing[12],
    right: 0,
    left: 0,
    height: componentSize.touchTarget,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing[2],
  },
  bar: {
    width: spacing[8],
    height: spacing[1],
    borderRadius: radius.round,
    backgroundColor: colors.divider,
  },
  activeBar: {
    backgroundColor: colors.primary.green,
  },
});
