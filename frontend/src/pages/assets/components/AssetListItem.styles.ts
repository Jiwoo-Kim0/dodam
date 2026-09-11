import { StyleSheet } from 'react-native';

import {
  colors,
  fontFamily,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  container: {
    minHeight: spacing[20],
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing[5],
    backgroundColor: colors.white,
  },
  divider: {
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.divider,
  },
  logo: {
    width: spacing[8],
    height: spacing[8],
    marginRight: spacing[4],
    resizeMode: 'contain',
  },
  information: {
    flex: 1,
    minWidth: 0,
    gap: spacing[1],
  },
  productName: {
    ...typography.body1,
    fontFamily: fontFamily.medium,
    color: colors.textPrimary,
  },
  description: {
    ...typography.caption,
    color: colors.textTertiary,
  },
  amount: {
    ...typography.subtitle,
    marginLeft: spacing[3],
    color: colors.textPrimary,
  },
});