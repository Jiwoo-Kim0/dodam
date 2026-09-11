import { StyleSheet } from 'react-native';

import { colors, componentSize, fontFamily, radius, semanticSpacing, spacing } from '@/styles';

export const styles = StyleSheet.create({
  screen: {
    backgroundColor: colors.background,
  },
  header: {
    justifyContent: 'center',
    minHeight: componentSize.touchTarget,
    paddingHorizontal: semanticSpacing.screenMargin,
  },
  backButton: {
    alignItems: 'flex-start',
    height: componentSize.touchTarget,
    justifyContent: 'center',
    width: componentSize.touchTarget,
  },
  pressed: {
    opacity: 0.62,
  },
  screenTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.bold,
    fontSize: 24,
    letterSpacing: -0.8,
    lineHeight: 30,
    marginTop: spacing[2],
    marginBottom: spacing[6],
  },
  scrollContent: {
    paddingBottom: spacing[6],
    paddingHorizontal: semanticSpacing.screenMargin,
  },
  sections: {
    gap: spacing[4],
  },
  card: {
    backgroundColor: colors.briefing.card,
    borderRadius: radius.card,
  },
  sectionTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: 16,
    lineHeight: 22,
  },
});
