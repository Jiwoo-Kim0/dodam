import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, spacing } from '@/styles';

export const styles = StyleSheet.create({
  upcomingCard: {
    alignItems: 'center',
    flexDirection: 'row',
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  upcomingIcon: {
    height: 28,
    marginRight: spacing[3],
    width: 28,
  },
  upcomingContent: {
    flex: 1,
    minWidth: 0,
  },
  /** 리포트 화면과 같은 카드 제목 규칙이다. */
  upcomingLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  upcomingTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body1,
    lineHeight: 20,
    marginTop: 2,
  },
  upcomingDescription: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.detail,
    lineHeight: 15,
  },
});
