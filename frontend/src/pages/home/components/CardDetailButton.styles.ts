import { StyleSheet } from 'react-native';

import { componentSize } from '@/styles';

export const styles = StyleSheet.create({
  button: {
    alignItems: 'center',
    height: componentSize.touchTarget,
    justifyContent: 'center',
    position: 'absolute',
    right: 4,
    top: 4,
    width: componentSize.touchTarget,
    zIndex: 5,
  },
  centered: {
    top: '50%',
    transform: [{ translateY: -(componentSize.touchTarget / 2) }],
  },
  pressed: {
    opacity: 0.55,
  },
});
