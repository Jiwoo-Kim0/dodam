import { StyleSheet } from 'react-native';

import { colors, radius, semanticSpacing, spacing } from '@/styles';

const BUTTON_SIZE = 56;

export const styles = StyleSheet.create({
  button: {
    alignItems: 'center',
    backgroundColor: colors.primary.lightGreen,
    borderColor: colors.report.scoreLight,
    borderRadius: radius.round,
    borderWidth: 1,
    bottom: spacing[5],
    height: BUTTON_SIZE,
    justifyContent: 'center',
    position: 'absolute',
    right: semanticSpacing.screenMargin,
    width: BUTTON_SIZE,
    // 목록 위에 떠 있어야 한다.
    zIndex: 10,
  },
  pressed: {
    opacity: 0.72,
  },
  iconWrap: {
    alignItems: 'center',
    height: 34,
    justifyContent: 'center',
    width: 34,
  },
  icon: {
    height: 34,
    width: 34,
  },
});
