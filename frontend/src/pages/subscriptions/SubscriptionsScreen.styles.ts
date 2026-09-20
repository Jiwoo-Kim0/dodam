import { StyleSheet } from 'react-native';

import {
  colors,
  componentSize,
  fontFamily,
  fontSize,
  semanticSpacing,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  screen: {
    backgroundColor: colors.background,
  },
  /** 제목이 본문 안에 있는 화면이라 머리글에는 뒤로 가기만 둔다. */
  header: {
    justifyContent: 'center',
    minHeight: componentSize.touchTarget,
    paddingHorizontal: spacing[3],
  },
  backButton: {
    alignItems: 'center',
    height: componentSize.touchTarget,
    justifyContent: 'center',
    width: componentSize.touchTarget,
  },
  pressed: {
    opacity: componentSize.bottomSheet.pressedOpacity,
  },
  scrollContent: {
    paddingBottom: spacing[8],
    paddingHorizontal: semanticSpacing.screenMargin,
    paddingTop: spacing[2],
  },
  headline: {
    color: colors.textPrimary,
    fontFamily: fontFamily.bold,
    fontSize: fontSize.headline,
    lineHeight: 36,
  },
  summary: {
    ...typography.body2,
    color: colors.textSecondary,
    lineHeight: 19,
    marginTop: spacing[3],
  },
  list: {
    gap: spacing[2],
    marginTop: spacing[4],
  },
});
