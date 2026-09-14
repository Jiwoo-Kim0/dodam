import { StyleSheet } from 'react-native';

import {
  colors,
  componentSize,
  semanticSpacing,
  spacing,
  typography,
} from '@/styles';

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
    minWidth: componentSize.touchTarget,
  },
  right: {
    alignItems: 'flex-end',
  },
  actionButton: {
    alignItems: 'center',
    height: componentSize.touchTarget,
    justifyContent: 'center',
    width: componentSize.touchTarget,
  },
  titleGroup: {
    alignItems: 'center',
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: spacing[2],
  },
  titleLine: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[2],
    maxWidth: '100%',
  },
  title: {
    ...typography.title,
    color: colors.textPrimary,
    flexShrink: 1,
    textAlign: 'center',
  },
  subtitle: {
    ...typography.caption,
    color: colors.textSecondary,
    marginTop: spacing[1],
  },
});
