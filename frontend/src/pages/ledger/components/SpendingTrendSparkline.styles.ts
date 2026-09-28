import { StyleSheet } from 'react-native';

import { colors, fontFamily, spacing } from '@/styles';

export const styles = StyleSheet.create({
  container: {
    alignItems: 'flex-end',
    flexShrink: 0,
    gap: spacing[1],
    width: 112,
  },
  caption: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: 9,
    lineHeight: 12,
  },
  legend: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[2],
  },
  legendItem: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[1],
  },
  legendLine: {
    borderRadius: 1,
    height: 2,
    width: 9,
  },
  previousLegendLine: {
    backgroundColor: colors.textTertiary,
    height: 1,
    opacity: 0.38,
  },
  legendLabel: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: 8,
    lineHeight: 10,
  },
});
