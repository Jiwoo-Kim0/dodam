import { StyleSheet } from 'react-native';

import {
  colors,
  componentSize,
  fontFamily,
  fontSize,
  semanticSpacing,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  screen: {
    backgroundColor: colors.background,
  },
  header: {
    minHeight: componentSize.touchTarget,
    justifyContent: 'center',
    paddingHorizontal: semanticSpacing.screenMargin,
  },
  backButton: {
    width: componentSize.touchTarget,
    height: componentSize.touchTarget,
    alignItems: 'flex-start',
    justifyContent: 'center',
  },
  backButtonPressed: {
    opacity: 0.5,
  },
  scrollContent: {
    paddingHorizontal: semanticSpacing.screenMargin,
    paddingBottom: spacing[8],
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[2],
    marginTop: spacing[1],
  },
  title: {
    ...typography.headline,
    color: colors.textPrimary,
  },
  acornIcon: {
    width: 22,
    height: 22,
  },
  dueDate: {
    ...typography.caption,
    color: colors.textSecondary,
    marginTop: spacing[1],
  },
  statusRow: {
    minHeight: 120,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: spacing[3],
  },
  statusText: {
    flex: 1,
  },
  dDay: {
    fontFamily: fontFamily.bold,
    fontSize: 42,
    lineHeight: 52,
    color: colors.textPrimary,
  },
  amountLabel: {
    ...typography.caption,
    color: colors.textSecondary,
    marginTop: spacing[1],
  },
  amountRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
    marginTop: spacing[2],
  },
  currentAmount: {
    fontFamily: fontFamily.bold,
    fontSize: fontSize.body1,
  },
  targetAmount: {
    ...typography.caption,
    color: colors.textSecondary,
  },
  characterImage: {
    width: spacing[24],
    height: spacing[24],
  },
  progressRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[3],
  },
  progressTrack: {
    height: spacing[3],
    flex: 1,
    overflow: 'hidden',
    borderRadius: spacing[2],
    backgroundColor: colors.divider,
  },
  progressFill: {
    height: '100%',
    borderRadius: spacing[2],
  },
  progressPercent: {
    ...typography.caption,
    width: spacing[8],
    color: colors.textSecondary,
    textAlign: 'right',
  },
  notFound: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing[4],
    paddingHorizontal: semanticSpacing.screenMargin,
  },
  notFoundTitle: {
    ...typography.title,
    color: colors.textPrimary,
  },
  notFoundAction: {
    ...typography.body1,
    color: colors.primary.green,
  },
});
