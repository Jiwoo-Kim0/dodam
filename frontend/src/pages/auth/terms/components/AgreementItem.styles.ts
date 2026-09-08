import { StyleSheet } from 'react-native';

import {
  colors,
  fontFamily,
  radius,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  container: {
    height: 52,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[4],
    paddingHorizontal: spacing[5],
    borderBottomWidth: 1,
    borderBottomColor: colors.divider,
  },

  emphasizedContainer: {
    height: 56,
    borderBottomWidth: 0,
    borderRadius: radius.default,
    backgroundColor: `${colors.primary.lightGreen}B3`,
  },

  checkbox: {
    width: spacing[5],
    height: spacing[5],
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: colors.border,
  },

  checkedBox: {
    borderColor: colors.primary.green,
    backgroundColor: colors.primary.green,
  },

  checkmark: {
    width: 6,
    height: 11,
    marginTop: -2,
    borderRightWidth: 2,
    borderBottomWidth: 2,
    borderColor: colors.white,
    transform: [{ rotate: '45deg' }],
  },

  label: {
    ...typography.body1,
    color: colors.textPrimary,
  },

  emphasizedLabel: {
    fontFamily: fontFamily.semiBold,
  },
});
