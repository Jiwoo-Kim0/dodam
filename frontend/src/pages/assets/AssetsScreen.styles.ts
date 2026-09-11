import { StyleSheet } from 'react-native';

import {
  colors,
  componentSize,
  semanticSpacing,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  screen: {
    backgroundColor: colors.background,
  },
  header: {
    minHeight: componentSize.touchTarget,
    justifyContent: 'center',
    paddingHorizontal: semanticSpacing.screenMargin,
  },
  backButton: {
    width: componentSize.touchTarget,
    height: componentSize.touchTarget,
    alignItems: 'flex-start',
    justifyContent: 'center',
  },
  backButtonPressed: {
    opacity: 0.5,
  },
  scrollContent: {
    paddingHorizontal: semanticSpacing.screenMargin,
    paddingBottom: spacing[8],
  },
  title: {
    ...typography.headline,
    marginTop: spacing[2],
    marginBottom: spacing[6],
    color: colors.textPrimary,
  },
  sections: {
    gap: spacing[5],
    marginTop: spacing[6],
  },
});