import { StyleSheet } from 'react-native';

import { colors, semanticSpacing, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingTop: semanticSpacing.screenTitleTop,
    paddingHorizontal: semanticSpacing.screenMargin,
  },

  title: {
    ...typography.headline,
    color: colors.textPrimary,
  },

  content: {
    flex: 1,
    marginTop: semanticSpacing.screenTitleContentGap,
  },

  description: {
    ...typography.body1,
    marginTop: spacing[2],
    color: colors.textSecondary,
    lineHeight: 20,
  },
});
