import { StyleSheet } from 'react-native';

import {
  colors,
  componentSize,
  fontFamily,
  radius,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  backdrop: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    backgroundColor: colors.overlay,
  },
  sheet: {
    maxHeight: componentSize.bottomSheet.maxHeight,
    paddingTop: spacing[6],
    paddingHorizontal: spacing[8],
    paddingBottom: spacing[6],
    borderTopLeftRadius: radius.sheet,
    borderTopRightRadius: radius.sheet,
    backgroundColor: colors.white,
  },
  header: {
    minHeight: componentSize.touchTarget,
    flexShrink: 0,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  title: {
    ...typography.subtitle,
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
  },
  closeButton: {
    width: componentSize.touchTarget,
    height: componentSize.touchTarget,
    alignItems: 'flex-end',
    justifyContent: 'center',
  },
  closeIcon: {
    color: colors.textPrimary,
    fontFamily: fontFamily.regular,
    fontSize: componentSize.icon.close,
  },
  optionList: {
    flexShrink: 1,
    marginTop: spacing[6],
    marginHorizontal: -spacing[3],
  },
  option: {
    minHeight: componentSize.bottomSheet.optionMinHeight,
    paddingVertical: spacing[3],
    justifyContent: 'center',
    paddingHorizontal: spacing[3],
    borderRadius: radius.default,
  },
  selectedOption: {
    backgroundColor: colors.background,
  },
  pressedOption: {
    opacity: componentSize.bottomSheet.pressedOpacity,
  },
  optionLabel: {
    ...typography.buttonSmall,
    color: colors.textPrimary,
  },
});
