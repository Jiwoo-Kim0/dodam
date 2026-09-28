import { StyleSheet } from 'react-native';

import { colors, radius, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  benefitList: {
    gap: spacing[3],
  },
  benefitCard: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
    borderRadius: radius.default,
    backgroundColor: colors.background,
  },
  benefitIcon: {
    width: 44,
    height: 44,
    marginRight: spacing[3],
  },
  benefitText: {
    flex: 1,
    gap: spacing[1],
  },
  benefitTitle: {
    ...typography.subtitle,
    color: colors.textPrimary,
  },
  benefitDescription: {
    ...typography.body1,
    color: colors.textSecondary,
    lineHeight: 20,
  },
});
