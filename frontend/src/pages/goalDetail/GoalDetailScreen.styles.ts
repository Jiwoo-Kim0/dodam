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
  headerDeleteButton: {
    minHeight: componentSize.touchTarget,
    minWidth: componentSize.touchTarget,
    alignItems: 'center',
    justifyContent: 'center',
  },
  headerDeleteLabel: {
    ...typography.caption,
    color: colors.textSecondary,
  },
  actionPressed: {
    opacity: componentSize.bottomSheet.pressedOpacity,
  },
  actionDisabled: {
    opacity: 0.5,
  },
  acornIcon: {
    width: 22,
    height: 22,
  },
  statusRow: {
    minHeight: 96,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: spacing[2],
  },
  statusText: {
    flex: 1,
  },
  dueDate: {
    ...typography.caption,
    color: colors.textSecondary,
    marginBottom: spacing[1],
  },
  dDay: {
    fontFamily: fontFamily.bold,
    fontSize: 38,
    lineHeight: 46,
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
    marginTop: spacing[1],
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
    width: spacing[20],
    height: spacing[20],
  },
  progressRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[2],
  },
  progressTrack: {
    height: spacing[2],
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
});
