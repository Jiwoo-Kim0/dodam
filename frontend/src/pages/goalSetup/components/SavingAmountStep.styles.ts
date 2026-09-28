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
  container: {
    flex: 1,
  },
  amountRow: {
    minHeight: componentSize.field.minHeight,
    flexDirection: 'row',
    alignItems: 'center',
    borderBottomWidth: 1,
    borderBottomColor: colors.divider,
  },
  amountInput: {
    ...typography.display,
    flex: 1,
    minWidth: 0,
    paddingHorizontal: 0,
    paddingVertical: 0,
    color: colors.textPrimary,
  },
  currency: {
    ...typography.title,
    color: colors.textPrimary,
  },
  clearButton: {
    width: componentSize.touchTarget,
    height: componentSize.touchTarget,
    alignItems: 'flex-end',
    justifyContent: 'center',
  },
  clearIcon: {
    color: colors.divider,
    fontFamily: fontFamily.regular,
    fontSize: componentSize.icon.clear,
  },
  optionList: {
    flexDirection: 'row',
    gap: spacing[2],
    marginTop: spacing[3],
  },
  option: {
    minHeight: componentSize.touchTarget,
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.default,
    backgroundColor: colors.background,
  },
  pressedOption: {
    opacity: 0.7,
  },
  optionLabel: {
    ...typography.body1,
    color: colors.textPrimary,
  },
});
