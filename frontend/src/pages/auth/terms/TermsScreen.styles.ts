import { StyleSheet } from 'react-native';

import {
  colors,
  componentSize,
  radius,
  semanticSpacing,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: semanticSpacing.screenMargin,
    backgroundColor: colors.background,
  },

  backButton: {
    position: 'absolute',
    top: spacing[12],
    left: spacing[3],
    zIndex: 1,
    width: 44,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },

  backIcon: {
    color: colors.textPrimary,
    fontSize: 36,
    lineHeight: 36,
  },

  header: {
    marginTop: semanticSpacing.screenTitleTop,
  },

  title: {
    ...typography.headline,
    color: colors.textPrimary,
  },

  description: {
    ...typography.body1,
    marginTop: spacing[2],
    color: colors.textSecondary,
    lineHeight: 20,
  },

  agreementSection: {
    marginTop: spacing[12],
  },

  agreementList: {
    marginTop: spacing[8],
  },

  continueButton: {
    height: componentSize.button.default,
    marginTop: 'auto',
    marginBottom: semanticSpacing.bottomActionOffset,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.default,
    backgroundColor: colors.primary.green,
  },

  continueButtonLabel: {
    ...typography.button,
    color: colors.white,
  },

  disabledButton: {
    backgroundColor: colors.disabledBackground,
  },

  pressedButton: {
    opacity: 0.8,
  },
});
