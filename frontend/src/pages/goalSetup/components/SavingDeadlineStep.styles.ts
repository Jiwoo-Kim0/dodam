import { StyleSheet } from 'react-native';

import {
  colors,
  componentSize,
  radius,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  dateField: {
    minHeight: componentSize.button.default,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: spacing[4],
    borderWidth: 1,
    borderColor: colors.divider,
    borderRadius: radius.default,
    backgroundColor: colors.white,
  },
  dateText: {
    ...typography.body1,
    color: colors.textPrimary,
  },
  placeholder: {
    color: colors.textTertiary,
  },
});