import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, spacing } from '@/styles';

export const styles = StyleSheet.create({
  goalCard: {
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[4],
  },
  goalImpactRow: {
    alignItems: 'stretch',
    flexDirection: 'row',
    marginTop: spacing[3],
  },
  goalImpactItem: {
    alignItems: 'center',
    flex: 1,
    flexDirection: 'row',
    minWidth: 0,
    paddingHorizontal: 2,
    position: 'relative',
  },
  goalDivider: {
    backgroundColor: colors.divider,
    bottom: 0,
    left: 0,
    position: 'absolute',
    top: 0,
    width: StyleSheet.hairlineWidth,
  },
  goalIcon: {
    height: 32,
    marginRight: spacing[1],
    width: 32,
  },
  goalTextContent: {
    flex: 1,
    minWidth: 0,
  },
  goalLabel: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: 11,
    lineHeight: 15,
  },
  goalValue: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.caption,
    lineHeight: 18,
    marginTop: 2,
  },
});
