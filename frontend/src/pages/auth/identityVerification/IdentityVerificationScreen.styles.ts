import { StyleSheet } from 'react-native';

import { colors, componentSize, fontFamily, fontSize, radius, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  form: {
    gap: spacing[4],
  },
  inputRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  input: {
    ...typography.input,
    flex: 1,
    minWidth: 0,
    paddingHorizontal: 0,
    paddingVertical: 0,
    color: colors.textPrimary,
  },
  clearButton: {
    width: componentSize.icon.clear,
    height: componentSize.icon.clear,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.round,
    backgroundColor: colors.border,
  },
  clearIcon: {
    color: colors.white,
    fontSize: componentSize.icon.small,
  },
  residentNumberRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  birthDateInput: {
    flex: 1,
    minWidth: 0,
  },
  residentSuffix: {
    flex: 1,
    minWidth: 0,
    flexDirection: 'row',
    alignItems: 'center',
  },
  residentSeparator: {
    marginHorizontal: spacing[2],
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.button,
  },
  residentDigitInput: {
    flex: 0,
    width: spacing[6],
    textAlign: 'center',
  },
  residentMask: {
    flex: 1,
    minWidth: 0,
    marginLeft: spacing[2],
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.button,
    letterSpacing: spacing[1],
  },
  carrierSelector: {
    minHeight: componentSize.field.selectorHeight,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[2],
    paddingHorizontal: spacing[2],
    borderRadius: radius.small,
    backgroundColor: 'transparent',
  },
  carrierLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body2,
  },
  selectedCarrierLabel: {
    color: colors.textPrimary,
  },
  carrierChevron: {
    width: componentSize.icon.chevron,
    height: componentSize.icon.chevron,
    borderRightWidth: componentSize.icon.stroke,
    borderBottomWidth: componentSize.icon.stroke,
    borderColor: colors.textSecondary,
    transform: [{ rotate: '45deg' }],
    marginTop: -spacing[1],
  },
  phoneInput: {
    marginLeft: spacing[4],
  },
});
