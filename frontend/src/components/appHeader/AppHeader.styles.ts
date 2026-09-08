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
  title: {
    ...typography.title,
    color: colors.textPrimary,
    flex: 1,
    paddingHorizontal: spacing[2],
    textAlign: 'center',
  },
});
