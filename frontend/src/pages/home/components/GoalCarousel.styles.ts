import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, semanticSpacing, spacing } from '@/styles';

export const styles = StyleSheet.create({
  container: {
    marginHorizontal: -semanticSpacing.screenMargin,
    marginTop: -spacing[2],
  },
  headerActions: {
    alignItems: 'flex-end',
    paddingHorizontal: semanticSpacing.screenMargin,
    marginBottom: spacing[2],
  },
  headerAddButton: {
    minHeight: 36,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[1],
    paddingHorizontal: spacing[3],
    borderRadius: radius.round,
    backgroundColor: colors.white,
  },
  headerAddIcon: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: 20,
    includeFontPadding: false,
    lineHeight: 18,
    textAlignVertical: 'center',
  },
  headerAddLabel: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.caption,
    includeFontPadding: false,
    lineHeight: 18,
    textAlignVertical: 'center',
    transform: [{ translateY: 2 }],
  },
  page: {
    paddingRight: spacing[2],
  },
  viewport: {
    overflow: 'hidden',
  },
  track: {
    flexDirection: 'row',
    paddingLeft: semanticSpacing.screenMargin,
  },
  dots: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[1],
    justifyContent: 'center',
    marginTop: spacing[2],
  },
  dot: {
    backgroundColor: colors.divider,
    borderRadius: radius.round,
    height: 6,
    width: 6,
  },
  dotActive: {
    backgroundColor: colors.primary.green,
    width: 16,
  },
  addGoalLabel: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
  },
  addGoalPressed: {
    opacity: 0.6,
  },
  status: {
    alignItems: 'center',
    backgroundColor: colors.white,
    borderRadius: radius.card,
    gap: spacing[3],
    justifyContent: 'center',
    marginHorizontal: 0,
    minHeight: 190,
  },
  statusText: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body1,
  },
});
