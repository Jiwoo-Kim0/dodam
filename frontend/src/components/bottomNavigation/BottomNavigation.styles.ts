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
    overflow: 'visible',
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
  featuredIconContainer: {
    backgroundColor: colors.primary.lightGreen,
    borderColor: colors.white,
    borderRadius: 28,
    borderWidth: 3,
    height: 56,
    marginTop: -18,
    width: 56,
  },
  featuredIconContainerFocused: {
    backgroundColor: colors.primary.yellow,
  },
  featuredIcon: {
    height: 38,
    width: 38,
  },
  featuredLabel: {
    color: colors.primary.brown,
    fontFamily: fontFamily.semiBold,
    marginTop: -7,
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
