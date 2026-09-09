import { StyleSheet } from 'react-native';

import { colors, radius, semanticSpacing, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingTop: semanticSpacing.screenTitleTop,
    paddingHorizontal: semanticSpacing.screenMargin,
    backgroundColor: colors.white,
  },
  title: {
    ...typography.headline,
    color: colors.textPrimary,
  },
  progressText: {
    ...typography.display,
    marginTop: spacing[12],
    color: colors.primary.green,
  },
  progressTrack: {
    width: '100%',
    height: 10,
    marginTop: spacing[4],
    overflow: 'hidden',
    borderRadius: radius.round,
    backgroundColor: colors.primary.lightGreen,
  },
  progressFill: {
    height: '100%',
    borderRadius: radius.round,
    backgroundColor: colors.primary.green,
  },
  bankList: {
    marginTop: spacing[8],
    gap: spacing[2],
  },
  bankRow: {
    minHeight: 56,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing[3],
    borderRadius: radius.default,
    backgroundColor: colors.background,
  },
  bankLogo: {
    width: 36,
    height: 36,
    resizeMode: 'contain',
  },
  bankName: {
    ...typography.body1,
    flex: 1,
    marginLeft: spacing[3],
    color: colors.textPrimary,
  },
  bankStatus: {
    ...typography.body2,
    color: colors.textTertiary,
  },
  doneStatus: {
    color: colors.primary.green,
  },
  syncingStatus: {
    color: colors.primary.orange,
  },
  notice: {
    ...typography.body2,
    marginTop: 'auto',
    marginBottom: semanticSpacing.bottomActionOffset,
    color: colors.textSecondary,
    textAlign: 'center',
    lineHeight: 19,
  },
});
