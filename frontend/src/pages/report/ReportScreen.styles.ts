import { StyleSheet } from 'react-native';

import {
  colors,
  fontFamily,
  fontSize,
  radius,
  semanticSpacing,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  screen: {
    backgroundColor: colors.background,
  },
  scrollContent: {
    paddingBottom: spacing[8],
    paddingHorizontal: semanticSpacing.screenMargin,
    paddingTop: spacing[2],
  },
  monthSelector: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[1],
  },
  monthLabel: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body1,
    lineHeight: 20,
  },
  screenDescription: {
    ...typography.body1,
    color: colors.textSecondary,
    lineHeight: 20,
  },
  sections: {
    gap: spacing[3],
    marginTop: spacing[3],
  },
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
  },
  stateContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 360,
    paddingHorizontal: spacing[6],
  },
  stateTitle: {
    ...typography.subtitle,
    color: colors.textPrimary,
    marginBottom: spacing[2],
  },
  stateText: {
    ...typography.body1,
    color: colors.textSecondary,
    marginTop: spacing[2],
    textAlign: 'center',
  },
  /** 가계부·브리핑의 작은 카드 제목과 같은 15/20 규칙을 사용한다. */
  cardTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.buttonSmall,
    lineHeight: 20,
  },
  cardDescription: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: 11,
    lineHeight: 15,
    marginTop: 2,
  },
});
