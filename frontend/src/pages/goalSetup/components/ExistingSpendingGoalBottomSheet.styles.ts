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
    backgroundColor: colors.overlay,
    bottom: 0,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
  sheet: {
    backgroundColor: colors.white,
    borderTopLeftRadius: radius.sheet,
    borderTopRightRadius: radius.sheet,
    paddingBottom: spacing[4],
    paddingHorizontal: spacing[5],
  },
  handle: {
    alignSelf: 'center',
    backgroundColor: colors.divider,
    borderRadius: radius.round,
    height: 4,
    marginTop: spacing[2],
    width: 36,
  },
  header: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: spacing[4],
  },
  titleGroup: {
    alignItems: 'center',
    flexDirection: 'row',
    flexShrink: 1,
    gap: spacing[2],
  },
  iconBackground: {
    alignItems: 'center',
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.round,
    height: 40,
    justifyContent: 'center',
    width: 40,
  },
  title: {
    ...typography.subtitle,
    color: colors.textPrimary,
    flexShrink: 1,
    fontFamily: fontFamily.semiBold,
  },
  closeButton: {
    alignItems: 'center',
    height: componentSize.touchTarget,
    justifyContent: 'center',
    marginLeft: spacing[2],
    width: componentSize.touchTarget,
  },
  description: {
    ...typography.body1,
    color: colors.textSecondary,
    lineHeight: 20,
    marginTop: spacing[3],
  },
  actions: {
    gap: spacing[2],
    marginTop: spacing[5],
  },
  primaryButton: {
    alignItems: 'center',
    backgroundColor: colors.primary.green,
    borderRadius: radius.default,
    justifyContent: 'center',
    minHeight: componentSize.button.default,
  },
  primaryButtonLabel: {
    ...typography.buttonSmall,
    color: colors.white,
    fontFamily: fontFamily.semiBold,
  },
  secondaryButton: {
    alignItems: 'center',
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.default,
    justifyContent: 'center',
    minHeight: componentSize.button.compact,
  },
  secondaryButtonLabel: {
    ...typography.buttonSmall,
    color: colors.report.scoreStrong,
    fontFamily: fontFamily.semiBold,
  },
  pressed: {
    opacity: componentSize.bottomSheet.pressedOpacity,
  },
});
