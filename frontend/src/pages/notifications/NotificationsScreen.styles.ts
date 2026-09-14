import { StyleSheet } from 'react-native';

import { colors, componentSize, semanticSpacing, spacing } from '@/styles';

export const styles = StyleSheet.create({
  screen: {
    backgroundColor: colors.background,
  },
  settingsButton: {
    alignItems: 'center',
    height: componentSize.touchTarget,
    justifyContent: 'center',
    width: componentSize.touchTarget,
  },
  scrollContent: {
    paddingBottom: spacing[6],
    paddingHorizontal: semanticSpacing.screenMargin,
    paddingTop: spacing[8],
  },
  list: {
    gap: spacing[3],
  },
});
