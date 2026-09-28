import { StyleSheet } from 'react-native';

import { semanticSpacing, spacing } from '@/styles';

const ICON_SIZE = 64;

export const styles = StyleSheet.create({
  /** 떠 있는 움직임은 바깥에서 주고, 화면에 고정하는 것도 여기서 맡는다. */
  floating: {
    bottom: spacing[5],
    position: 'absolute',
    right: semanticSpacing.screenMargin,
    // 목록 위에 떠 있어야 한다.
    zIndex: 10,
  },
  button: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  pressed: {
    opacity: 0.72,
  },
  icon: {
    height: ICON_SIZE,
    width: ICON_SIZE,
  },
});
