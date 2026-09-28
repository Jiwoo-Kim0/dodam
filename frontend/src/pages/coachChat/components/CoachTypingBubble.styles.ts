import { StyleSheet } from 'react-native';

import { colors, radius, spacing } from '@/styles';

export const styles = StyleSheet.create({
  bubble: {
    alignItems: 'center',
    backgroundColor: colors.white,
    borderBottomLeftRadius: radius.small,
    borderRadius: radius.card,
    flexDirection: 'row',
    gap: spacing[1],
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  dot: {
    backgroundColor: colors.report.scoreLight,
    borderRadius: radius.round,
    height: 7,
    width: 7,
  },
});
