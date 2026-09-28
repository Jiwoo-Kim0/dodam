import { StyleSheet } from 'react-native';

import {
  colors,
  componentSize,
  fontFamily,
  radius,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  backdrop: {
    backgroundColor: colors.overlay,
    bottom: 0,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
  sheet: {
    backgroundColor: colors.white,
    borderTopLeftRadius: radius.sheet,
    borderTopRightRadius: radius.sheet,
    maxHeight: componentSize.bottomSheet.maxHeight,
    minHeight: 430,
    paddingBottom: spacing[5],
    paddingHorizontal: spacing[5],
    paddingTop: spacing[5],
  },
  header: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    minHeight: componentSize.touchTarget,
  },
  sheetTitle: {
    ...typography.title,
    color: colors.textPrimary,
  },
  headerButton: {
    alignItems: 'flex-end',
    height: componentSize.touchTarget,
    justifyContent: 'center',
    width: componentSize.touchTarget,
  },
  status: {
    alignItems: 'center',
    flex: 1,
    gap: spacing[4],
    justifyContent: 'center',
    minHeight: 300,
  },
  statusText: {
    ...typography.body1,
    color: colors.textSecondary,
    textAlign: 'center',
  },
  retryButton: {
    alignItems: 'center',
    borderColor: colors.primary.green,
    borderRadius: radius.default,
    borderWidth: 1,
    justifyContent: 'center',
    minHeight: componentSize.button.compact,
    paddingHorizontal: spacing[5],
  },
  retryLabel: {
    ...typography.buttonSmall,
    color: colors.primary.green,
  },
  content: {
    alignItems: 'center',
    paddingBottom: spacing[6],
    paddingTop: spacing[4],
  },
  icon: {
    height: 72,
    width: 72,
  },
  title: {
    ...typography.subtitle,
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    marginTop: spacing[4],
    textAlign: 'center',
  },
  createdAt: {
    ...typography.caption,
    color: colors.textTertiary,
    marginTop: spacing[2],
  },
  message: {
    ...typography.body1,
    color: colors.textSecondary,
    lineHeight: 22,
    marginTop: spacing[5],
    textAlign: 'center',
  },
  actions: {
    flexDirection: 'row',
    gap: spacing[3],
    paddingTop: spacing[4],
  },
  deleteButton: {
    alignItems: 'center',
    borderColor: colors.border,
    borderRadius: radius.default,
    borderWidth: 1,
    flex: 1,
    justifyContent: 'center',
    minHeight: componentSize.button.default,
  },
  deleteLabel: {
    ...typography.buttonSmall,
    color: colors.textSecondary,
  },
  moveButton: {
    alignItems: 'center',
    backgroundColor: colors.primary.green,
    borderRadius: radius.default,
    flex: 2,
    justifyContent: 'center',
    minHeight: componentSize.button.default,
  },
  moveLabel: {
    ...typography.buttonSmall,
    color: colors.white,
  },
  pressed: {
    opacity: componentSize.bottomSheet.pressedOpacity,
  },
});
