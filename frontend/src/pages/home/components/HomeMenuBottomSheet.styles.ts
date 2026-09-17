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
    paddingHorizontal: spacing[5],
    paddingBottom: spacing[4],
    borderTopLeftRadius: radius.sheet,
    borderTopRightRadius: radius.sheet,
    backgroundColor: colors.white,
  },
  handle: {
    width: 36,
    height: 4,
    alignSelf: 'center',
    marginTop: spacing[2],
    borderRadius: radius.round,
    backgroundColor: colors.divider,
  },
  header: {
    minHeight: 84,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  title: {
    ...typography.title,
    color: colors.textPrimary,
  },
  description: {
    ...typography.caption,
    marginTop: spacing[1],
    color: colors.textSecondary,
  },
  closeButton: {
    width: componentSize.touchTarget,
    height: componentSize.touchTarget,
    alignItems: 'center',
    justifyContent: 'center',
  },
  menuList: {
    gap: spacing[2],
    paddingBottom: spacing[2],
  },
  menuItem: {
    minHeight: 76,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[3],
    borderRadius: radius.default,
    backgroundColor: colors.background,
  },
  iconBackground: {
    width: 44,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: spacing[3],
    borderRadius: radius.round,
    backgroundColor: colors.primary.lightGreen,
  },
  logoutIconBackground: {
    backgroundColor: colors.goal.reducingCharacterBackground,
  },
  menuCopy: {
    flex: 1,
  },
  menuLabel: {
    ...typography.body1,
    fontFamily: fontFamily.semiBold,
    color: colors.textPrimary,
  },
  logoutLabel: {
    color: colors.primary.orange,
  },
  menuDescription: {
    ...typography.caption,
    marginTop: spacing[1],
    color: colors.textSecondary,
  },
  chevron: {
    ...typography.title,
    marginLeft: spacing[2],
    color: colors.textTertiary,
  },
  pressed: {
    opacity: componentSize.bottomSheet.pressedOpacity,
  },
  disabled: {
    opacity: 0.5,
  },
});
