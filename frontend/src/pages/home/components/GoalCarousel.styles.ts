import { StyleSheet } from 'react-native';

import { colors, radius, semanticSpacing, spacing } from '@/styles';

export const styles = StyleSheet.create({
  container: {
    marginHorizontal: -semanticSpacing.screenMargin,
    marginTop: spacing[4],
  },
  page: {
    paddingRight: spacing[2],
  },
  viewport: {
    overflow: 'hidden',
  },
  track: {
    flexDirection: 'row',
    paddingLeft: semanticSpacing.screenMargin,
  },
  dots: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[1],
    justifyContent: 'center',
    marginTop: spacing[2],
  },
  dot: {
    backgroundColor: colors.divider,
    borderRadius: radius.round,
    height: 6,
    width: 6,
  },
  dotActive: {
    backgroundColor: colors.primary.orange,
    width: 16,
  },
});
