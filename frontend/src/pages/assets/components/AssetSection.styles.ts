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
    gap: spacing[2],
  },
  header: {
    minHeight: spacing[8],
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: spacing[3],
  },
  title: {
    ...typography.title,
    flexShrink: 0,
    color: colors.textPrimary,
  },
  total: {
    ...typography.body1,
    flexShrink: 1,
    fontFamily: fontFamily.medium,
    color: colors.textPrimary,
    textAlign: 'right',
  },
  list: {
    overflow: 'hidden',
    borderRadius: radius.card,
    backgroundColor: colors.white,
  },
});