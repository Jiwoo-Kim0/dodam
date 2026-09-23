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
  /** 통장을 고르기 전에 읽히도록 목록 바로 위에 둔다. */
  accountTip: {
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.default,
    gap: spacing[1],
    marginTop: spacing[2],
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[3],
  },
  accountTipTitle: {
    ...typography.body2,
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
  },
  accountTipText: {
    ...typography.caption,
    color: colors.textSecondary,
    lineHeight: 17,
  },
});