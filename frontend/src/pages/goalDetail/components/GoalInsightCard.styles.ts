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
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[4],
    borderRadius: radius.card,
    backgroundColor: colors.white,
  },
  summary: {
    minHeight: spacing[20],
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing[3],
  },
  illustration: {
    width: 64,
    height: 64,
  },
  message: {
    flex: 1,
  },
  eyebrow: {
    ...typography.body2,
    color: colors.textSecondary,
  },
  highlight: {
    ...typography.subtitle,
    fontFamily: fontFamily.semiBold,
    marginTop: spacing[1],
    color: colors.textPrimary,
  },
  description: {
    ...typography.caption,
    marginTop: spacing[1],
    color: colors.textSecondary,
    lineHeight: 18,
  },
  divider: {
    height: StyleSheet.hairlineWidth,
    marginVertical: spacing[3],
    backgroundColor: colors.divider,
  },
  metrics: {
    minHeight: 40,
    flexDirection: 'row',
    alignItems: 'stretch',
  },
  metric: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing[1],
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
