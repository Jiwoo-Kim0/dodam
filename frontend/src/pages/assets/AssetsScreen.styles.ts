import { StyleSheet } from 'react-native';

import {
  colors,
  radius,
  semanticSpacing,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  screen: {
    backgroundColor: colors.background,
    flex: 1,
  },
  statusContainer: {
    alignItems: 'center',
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: semanticSpacing.screenMargin,
  },
  statusTitle: {
    ...typography.subtitle,
    color: colors.textPrimary,
    textAlign: 'center',
  },
  statusDescription: {
    ...typography.body2,
    color: colors.textSecondary,
    marginTop: spacing[2],
    textAlign: 'center',
  },
  retryButton: {
    backgroundColor: colors.primary.green,
    borderRadius: radius.round,
    marginTop: spacing[5],
    paddingHorizontal: spacing[5],
    paddingVertical: spacing[3],
  },
  retryButtonPressed: {
    opacity: 0.82,
  },
  retryButtonLabel: {
    ...typography.buttonSmall,
    color: colors.white,
  },
  scrollContent: {
    paddingBottom: spacing[8],
    paddingHorizontal: semanticSpacing.screenMargin,
    paddingTop: spacing[2],
  },
  sections: {
    gap: spacing[4],
    marginTop: spacing[4],
  },
});
