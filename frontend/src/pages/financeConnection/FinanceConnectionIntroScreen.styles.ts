import { StyleSheet } from 'react-native';

import { colors, semanticSpacing, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingTop: semanticSpacing.screenTitleTop,
    paddingHorizontal: semanticSpacing.screenMargin,
    backgroundColor: colors.white,
  },
  header: {
    gap: spacing[2],
  },
  title: {
    ...typography.headline,
    color: colors.textPrimary,
    lineHeight: 36,
  },
  description: {
    ...typography.body1,
    color: colors.textSecondary,
    lineHeight: 20,
  },
  visualArea: {
    flex: 1,
    justifyContent: 'center',
    paddingVertical: spacing[6],
  },
});
