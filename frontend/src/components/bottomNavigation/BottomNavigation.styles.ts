import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, spacing } from '@/styles';

export const styles = StyleSheet.create({
  container: {
    backgroundColor: colors.background,
    flex: 1,
  },
  content: {
    flex: 1,
  },
  tabList: {
    alignItems: 'center',
    backgroundColor: colors.background,
    flexDirection: 'row',
    height: 64,
    paddingHorizontal: spacing[2],
  },
  tab: {
    alignItems: 'center',
    flex: 1,
    height: '100%',
    justifyContent: 'center',
  },
  iconContainer: {
    alignItems: 'center',
    height: 34,
    justifyContent: 'center',
    width: 64,
  },
  icon: {
    height: 24,
    width: 24,
  },
  label: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 16,
  },
  selectedLabel: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
  },
});
