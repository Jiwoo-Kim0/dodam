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
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    padding: spacing[4],
  },
  /** 리포트 화면과 같은 카드 제목 규칙이다. */
  title: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 18,
    marginBottom: semanticSpacing.cardTitleContentGap,
  },
  row: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[3],
    justifyContent: 'space-between',
  },
  labelGroup: {
    flex: 1,
    gap: spacing[1],
  },
  label: {
    ...typography.body2,
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
  },
  description: {
    ...typography.caption,
    color: colors.textSecondary,
  },
  amount: {
    ...typography.body2,
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    textAlign: 'right',
  },
  divider: {
    backgroundColor: colors.divider,
    height: StyleSheet.hairlineWidth,
    marginVertical: spacing[3],
  },
});
