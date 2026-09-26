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
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[2],
  },
  action: {
    paddingHorizontal: spacing[2],
    paddingVertical: spacing[1],
    borderRadius: radius.default,
    backgroundColor: colors.primary.lightGreen,
  },
  actionPressed: {
    opacity: 0.7,
  },
  actionLabel: {
    ...typography.caption,
    fontFamily: fontFamily.semiBold,
    color: colors.primary.green,
  },
  title: {
    ...typography.subtitle,
    flexShrink: 0,
    fontFamily: fontFamily.semiBold,
    color: colors.textPrimary,
  },
  total: {
    ...typography.caption,
    flexShrink: 1,
    fontFamily: fontFamily.medium,
    color: colors.textSecondary,
    textAlign: 'right',
  },
  list: {
    overflow: 'hidden',
    borderRadius: radius.card,
    backgroundColor: colors.white,
  },
  emptyText: {
    ...typography.body2,
    color: colors.textSecondary,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[5],
    textAlign: 'center',
  },
});
