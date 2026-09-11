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
    height: 560,
    maxHeight: componentSize.bottomSheet.maxHeight,
    padding: spacing[5],
    borderTopLeftRadius: radius.sheet,
    borderTopRightRadius: radius.sheet,
    backgroundColor: colors.white,
  },
  header: {
    minHeight: componentSize.touchTarget,
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
  },
  title: {
    ...typography.title,
    fontFamily: fontFamily.semiBold,
    color: colors.textPrimary,
  },
  description: {
    ...typography.body2,
    marginTop: spacing[1],
    color: colors.textSecondary,
  },
  closeButton: {
    width: componentSize.touchTarget,
    height: componentSize.touchTarget,
    alignItems: 'flex-end',
    justifyContent: 'flex-start',
  },
  closeIcon: {
    ...typography.title,
    color: colors.textPrimary,
  },
  accountSelection: {
    flex: 1,
    marginTop: spacing[6],
  },
  completeButton: {
    minHeight: componentSize.button.default,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: spacing[4],
    borderRadius: radius.default,
    backgroundColor: colors.primary.green,
  },
  completeButtonLabel: {
    ...typography.button,
    color: colors.white,
  },
  disabledButton: {
    backgroundColor: colors.disabledBackground,
  },
  pressedButton: {
    opacity: componentSize.bottomSheet.pressedOpacity,
  },
});
