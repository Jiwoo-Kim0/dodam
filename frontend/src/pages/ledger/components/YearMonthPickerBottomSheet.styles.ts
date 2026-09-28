import { StyleSheet } from 'react-native';

import {
  colors,
  componentSize,
  radius,
  spacing,
  typography,
} from '@/styles';

const VISIBLE_ITEM_COUNT = 4;
const SIDE_ITEM_COUNT = (VISIBLE_ITEM_COUNT - 1) / 2;

export const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  backdrop: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    backgroundColor: colors.overlay,
  },
  sheet: {
    height: componentSize.bottomSheet.height,
    maxHeight: componentSize.bottomSheet.maxHeight,
    paddingTop: spacing[6],
    paddingHorizontal: spacing[5],
    paddingBottom: spacing[6],
    borderTopLeftRadius: radius.sheet,
    borderTopRightRadius: radius.sheet,
    backgroundColor: colors.white,
    overflow: 'hidden',
  },
  title: {
    ...typography.subtitle,
    color: colors.textPrimary,
    textAlign: 'center',
  },
  picker: {
    position: 'relative',
    height: componentSize.touchTarget * VISIBLE_ITEM_COUNT,
    flexDirection: 'row',
    gap: spacing[1],
    marginTop: spacing[5],
    overflow: 'hidden',
  },
  selectionRow: {
    position: 'absolute',
    top: componentSize.touchTarget * SIDE_ITEM_COUNT,
    right: 0,
    left: 0,
    height: componentSize.touchTarget,
    flexDirection: 'row',
    gap: spacing[1],
  },
  selectionCell: {
    flex: 1,
    borderRadius: radius.default,
    backgroundColor: colors.background,
  },
  column: {
    flex: 1,
  },
  columnContent: {
    paddingVertical: componentSize.touchTarget * SIDE_ITEM_COUNT,
  },
  option: {
    height: componentSize.touchTarget,
    alignItems: 'center',
    justifyContent: 'center',
  },
  optionLabel: {
    ...typography.body1,
    color: colors.textTertiary,
  },
  selectedOptionLabel: {
    color: colors.textPrimary,
  },
  confirmButton: {
    height: componentSize.button.default,
    marginTop: 'auto',
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.default,
    backgroundColor: colors.primary.green,
  },
  confirmLabel: {
    ...typography.button,
    color: colors.white,
  },
});
