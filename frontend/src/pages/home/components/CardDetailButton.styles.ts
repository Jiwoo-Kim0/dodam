import { StyleSheet } from 'react-native';

import { componentSize } from '@/styles';

export const styles = StyleSheet.create({
  button: {
    alignItems: 'center',
    bottom: 0,
    justifyContent: 'center',
    position: 'absolute',
    right: 4,
    top: 0,
    width: componentSize.touchTarget,
    zIndex: 5,
  },
});
