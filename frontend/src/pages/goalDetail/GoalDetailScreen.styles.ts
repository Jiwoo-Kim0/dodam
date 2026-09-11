import { StyleSheet } from 'react-native';

import {
  colors,
  componentSize,
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
  actions: {
    flexDirection: 'row',
    gap: spacing[2],
    paddingHorizontal: semanticSpacing.screenMargin,
    paddingVertical: spacing[3],
    backgroundColor: colors.background,
  },
  editButton: {
    minHeight: componentSize.button.compact,
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.default,
    backgroundColor: colors.primary.green,
  },
  editButtonLabel: {
    ...typography.buttonSmall,
    color: colors.white,
  },
  otherGoalsButton: {
    minHeight: componentSize.button.compact,
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: colors.primary.green,
    borderRadius: radius.default,
    backgroundColor: colors.white,
  },
  otherGoalsButtonLabel: {
    ...typography.buttonSmall,
    color: colors.primary.green,
  },
  actionPressed: {
    opacity: componentSize.bottomSheet.pressedOpacity,
  },
  acornIcon: {
    width: 22,
    height: 22,
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
