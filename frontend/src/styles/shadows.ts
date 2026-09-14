import type { ViewStyle } from 'react-native';

import { colors } from './colors';

export const shadows = {
  softCard: {
    elevation: 1,
    shadowColor: colors.textPrimary,
    shadowOffset: { height: 2, width: 0 },
    shadowOpacity: 0.08,
    shadowRadius: 3,
  },
} as const satisfies Record<string, ViewStyle>;
