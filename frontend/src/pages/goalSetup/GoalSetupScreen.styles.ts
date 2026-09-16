import { StyleSheet } from 'react-native';

import { colors, typography } from '@/styles';

export const styles = StyleSheet.create({
  content: {
    flex: 1,
  },
  statusContent: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  statusText: {
    ...typography.body1,
    color: colors.textSecondary,
    textAlign: 'center',
  },
});
