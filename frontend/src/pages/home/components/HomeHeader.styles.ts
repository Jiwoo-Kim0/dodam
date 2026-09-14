import { StyleSheet } from 'react-native';

import { colors, componentSize, semanticSpacing } from '@/styles';

export const HOME_HEADER_HEIGHT = 56;
/** dodam_logo_image.png의 원본 가로/세로 비율이다. */
const LOGO_RATIO = 270 / 250;
const LOGO_HEIGHT = 56;
const LOGO_TRANSPARENT_MARGIN_SCALE = 1.45;

export const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    backgroundColor: colors.white,
    flexDirection: 'row',
    height: HOME_HEADER_HEIGHT,
    justifyContent: 'space-between',
    paddingHorizontal: semanticSpacing.screenMargin,
  },
  logo: {
    height: LOGO_HEIGHT,
    transform: [{ scale: LOGO_TRANSPARENT_MARGIN_SCALE }],
    width: LOGO_HEIGHT * LOGO_RATIO,
  },
  actions: {
    flexDirection: 'row',
  },
  actionButton: {
    alignItems: 'center',
    height: componentSize.touchTarget,
    justifyContent: 'center',
    width: 36,
  },
  actionButtonPressed: {
    opacity: 0.5,
  },
});
