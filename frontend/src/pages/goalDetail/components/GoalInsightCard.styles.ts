import { StyleSheet } from 'react-native';

import {
  colors,
  fontFamily,
  radius,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  card: {
    marginTop: spacing[5],
    paddingHorizontal: spacing[5],
    paddingVertical: spacing[5],
    borderRadius: radius.card,
    backgroundColor: colors.white,
  },
  summary: {
    minHeight: spacing[24],
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing[5],
  },
  illustration: {
    width: spacing[20],
    height: spacing[20],
  },
  message: {
    flex: 1,
  },
  eyebrow: {
    ...typography.body2,
    color: colors.textSecondary,
  },
  highlight: {
    ...typography.title,
    marginTop: spacing[1],
    color: colors.textPrimary,
  },
  description: {
    ...typography.caption,
    marginTop: spacing[2],
    color: colors.textSecondary,
    lineHeight: 18,
  },
  divider: {
    height: StyleSheet.hairlineWidth,
    marginVertical: spacing[4],
    backgroundColor: colors.divider,
  },
  metrics: {
    minHeight: spacing[12],
    flexDirection: 'row',
    alignItems: 'stretch',
  },
  metric: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing[2],
  },
  metricDivider: {
    width: StyleSheet.hairlineWidth,
    backgroundColor: colors.divider,
  },
  metricLabel: {
    ...typography.caption,
    color: colors.textSecondary,
  },
  metricValue: {
    ...typography.body1,
    fontFamily: fontFamily.semiBold,
    color: colors.textPrimary,
  },
});
