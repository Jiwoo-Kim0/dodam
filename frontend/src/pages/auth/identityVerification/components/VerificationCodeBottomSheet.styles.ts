import { StyleSheet } from 'react-native';

import {
  colors,
  componentSize,
  radius,
  semanticSpacing,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  overlay: {
    flex: 1,
  },
  backdrop: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    backgroundColor: colors.overlay,
  },
  keyboardContainer: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  sheet: {
    height: componentSize.bottomSheet.height,
    maxHeight: componentSize.bottomSheet.maxHeight,
    backgroundColor: colors.white,
    borderTopLeftRadius: radius.sheet,
    borderTopRightRadius: radius.sheet,
    overflow: 'hidden',
  },
  content: {
    paddingTop: spacing[6],
    paddingHorizontal: semanticSpacing.screenMargin,
    paddingBottom: spacing[8],
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    minHeight: componentSize.touchTarget,
  },
  title: {
    ...typography.subtitle,
    color: colors.textPrimary,
    flexShrink: 1,
  },
  closeButton: {
    width: componentSize.touchTarget,
    height: componentSize.touchTarget,
    alignItems: 'flex-end',
    justifyContent: 'center',
  },
  closeIcon: {
    ...typography.body1,
    fontSize: componentSize.icon.close,
    color: colors.textPrimary,
  },
  phoneSummary: {
    ...typography.body1,
    color: colors.textPrimary,
    marginTop: spacing[2],
  },
  inputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: componentSize.field.minHeight,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[2],
    marginTop: spacing[8],
    gap: spacing[2],
    borderRadius: radius.default,
    backgroundColor: colors.background,
  },
  input: {
    ...typography.input,
    flex: 1,
    minWidth: 0,
    paddingVertical: 0,
    paddingHorizontal: 0,
    color: colors.textPrimary,
  },
  clearButton: {
    width: componentSize.icon.clear,
    height: componentSize.touchTarget,
    alignItems: 'center',
    justifyContent: 'center',
  },
  clearIcon: {
    ...typography.body1,
    fontSize: componentSize.icon.clear,
    color: colors.border,
  },
  resendButton: {
    minHeight: componentSize.touchTarget,
    justifyContent: 'center',
    paddingHorizontal: spacing[3],
    borderRadius: radius.small,
    backgroundColor: colors.white,
  },
  resendLabel: {
    ...typography.body1,
    color: colors.textPrimary,
  },
  nextButton: {
    minHeight: componentSize.button.default,
    marginTop: spacing[6],
    paddingVertical: spacing[3],
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.default,
    backgroundColor: colors.primary.green,
  },
  disabledButton: {
    backgroundColor: colors.disabledBackground,
  },
  pressed: {
    opacity: componentSize.bottomSheet.pressedOpacity,
  },
  nextLabel: {
    ...typography.button,
    color: colors.white,
  },
  helpButton: {
    minHeight: componentSize.touchTarget,
    marginTop: spacing[2],
    alignItems: 'center',
    justifyContent: 'center',
  },
  helpLabel: {
    ...typography.body2,
    color: colors.textSecondary,
    textDecorationLine: 'underline',
  },
});
