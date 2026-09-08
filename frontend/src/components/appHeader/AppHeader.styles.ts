import { StyleSheet } from 'react-native';

import { colors, semanticSpacing, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    flexDirection: 'row',
    minHeight: 56,
    paddingHorizontal: semanticSpacing.screenMargin,
  },
  side: {
    alignItems: 'flex-start',
    justifyContent: 'center',
    minWidth: 44,
  },
  right: {
    alignItems: 'flex-end',
  },
  actionButton: {
    alignItems: 'center',
    height: 44,
    justifyContent: 'center',
    width: 44,
  },
  title: {
    ...typography.title,
    color: colors.textPrimary,
    flex: 1,
    paddingHorizontal: spacing[2],
    textAlign: 'center',
  },
});
