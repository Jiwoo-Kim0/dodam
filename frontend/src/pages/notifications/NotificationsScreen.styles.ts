import { StyleSheet } from 'react-native';

import {
  colors,
  componentSize,
  fontFamily,
  radius,
  semanticSpacing,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  screen: {
    backgroundColor: colors.background,
  },
  readAllButton: {
    alignItems: 'center',
    height: componentSize.touchTarget,
    justifyContent: 'center',
    minWidth: 60,
  },
  readAllLabel: {
    ...typography.caption,
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
  },
  readAllLabelDisabled: {
    color: colors.textTertiary,
  },
  scrollContent: {
    paddingBottom: spacing[6],
    paddingHorizontal: semanticSpacing.screenMargin,
    paddingTop: spacing[8],
  },
  scrollDismissArea: {
    flex: 1,
  },
  list: {
    gap: spacing[3],
  },
  status: {
    alignItems: 'center',
    gap: spacing[4],
    justifyContent: 'center',
    minHeight: 360,
  },
  emptyTitle: {
    ...typography.subtitle,
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
  },
  statusText: {
    ...typography.body1,
    color: colors.textSecondary,
    lineHeight: 21,
    textAlign: 'center',
  },
  retryButton: {
    alignItems: 'center',
    borderColor: colors.primary.green,
    borderRadius: radius.default,
    borderWidth: 1,
    justifyContent: 'center',
    minHeight: componentSize.button.compact,
    paddingHorizontal: spacing[5],
  },
  retryLabel: {
    ...typography.buttonSmall,
    color: colors.primary.green,
  },
  moreIndicator: {
    marginVertical: spacing[4],
  },
  pressed: {
    opacity: componentSize.bottomSheet.pressedOpacity,
  },
});
