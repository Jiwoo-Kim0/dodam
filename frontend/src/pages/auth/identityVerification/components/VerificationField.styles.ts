import { StyleSheet } from 'react-native';

import { colors, componentSize, radius, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  container: {
    minHeight: componentSize.field.minHeight,
    paddingVertical: spacing[2],
    paddingHorizontal: spacing[4],
    justifyContent: 'center',
    borderRadius: radius.default,
    backgroundColor: colors.background,
  },

  label: {
    ...typography.body2,
    color: colors.textTertiary,
  },

  content: {
    minHeight: spacing[6],
    marginTop: spacing[1],
    justifyContent: 'center',
  },
});
