import { StyleSheet } from 'react-native';

import { colors, semanticSpacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingTop: semanticSpacing.screenTitleTop,
    paddingHorizontal: semanticSpacing.screenMargin,
    backgroundColor: colors.background,
  },

  title: {
    ...typography.headline,
    color: colors.textPrimary,
  },

  content: {
    flex: 1,
    marginTop: semanticSpacing.screenTitleContentGap,
  },
});
