import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, semanticSpacing, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  metricsCard: {
    padding: spacing[4],
  },
  metricGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing[2],
    marginTop: semanticSpacing.cardTitleContentGap,
  },
  metricTile: {
    backgroundColor: colors.background,
    borderRadius: radius.card,
    flexBasis: '48%',
    flexGrow: 1,
    minHeight: 64,
    paddingHorizontal: spacing[2],
    paddingVertical: spacing[2],
  },
  metricTileAccent: {
    backgroundColor: colors.report.patternSurface,
  },
  metricHeader: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  metricLabel: {
    ...typography.caption,
    color: colors.textSecondary,
    flexShrink: 1,
    lineHeight: 17,
  },
  metricIconFrame: {
    alignItems: 'center',
    height: 22,
    justifyContent: 'center',
    marginTop: -2,
    width: 22,
  },
  metricIcon: {
    height: 22,
    width: 22,
  },
  metricValueRow: {
    alignItems: 'baseline',
    flexDirection: 'row',
  },
  metricValue: {
    color: colors.textPrimary,
    flexShrink: 1,
    fontFamily: fontFamily.bold,
    fontSize: fontSize.subtitle,
    letterSpacing: -0.6,
    lineHeight: 24,
  },
  metricValueUnit: {
    ...typography.caption,
    color: colors.textPrimary,
    letterSpacing: -0.2,
  },
  metricChange: {
    ...typography.caption,
    color: colors.textSecondary,
    lineHeight: 17,
  },
});
