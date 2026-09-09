import { StyleSheet } from 'react-native';

import {
  colors,
  fontFamily,
  radius,
  semanticSpacing,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: semanticSpacing.screenMargin,
    backgroundColor: colors.white,
  },
  content: {
    flex: 1,
    alignItems: 'center',
    paddingTop: spacing[20],
  },
  character: {
    width: 140,
    height: 140,
    resizeMode: 'contain',
  },
  title: {
    ...typography.title,
    marginTop: spacing[4],
    color: colors.textPrimary,
    textAlign: 'center',
  },
  summaryCard: {
    width: '100%',
    marginTop: spacing[6],
    padding: spacing[5],
    borderWidth: 1,
    borderColor: colors.primary.green,
    borderRadius: radius.default,
    backgroundColor: colors.white,
  },
  goalHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing[2],
    marginBottom: spacing[1],
  },
  acornIcon: {
    width: spacing[6],
    height: spacing[6],
    resizeMode: 'contain',
  },
  goalName: {
    ...typography.subtitle,
    color: colors.textPrimary,
  },
  infoRow: {
    minHeight: spacing[6],
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: spacing[4],
  },
  infoLabelGroup: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[2],
  },
  infoBullet: {
    width: 6,
    height: 6,
    borderRadius: radius.round,
    backgroundColor: colors.primary.green,
  },
  infoLabel: {
    ...typography.body2,
    color: colors.textSecondary,
  },
  infoValue: {
    ...typography.body1,
    color: colors.textPrimary,
  },
  highlightValue: {
    ...typography.body1,
    fontFamily: fontFamily.semiBold,
    color: colors.primary.green,
  },
  connector: {
    height: spacing[12],
    alignItems: 'center',
  },
  connectorLine: {
    flex: 1,
    width: 1,
    backgroundColor: colors.primary.green,
  },
  connectorDot: {
    width: 6,
    height: 6,
    borderRadius: radius.round,
    backgroundColor: colors.primary.green,
  },
  dDayBox: {
    marginTop: spacing[3],
    paddingVertical: spacing[5],
    paddingHorizontal: spacing[2],
    alignItems: 'center',
    borderRadius: radius.default,
    backgroundColor: colors.primary.lightGreen,
  },
  dDay: {
    ...typography.title,
    color: colors.textPrimary,
  },
  dDayDescription: {
    ...typography.body2,
    marginTop: spacing[2],
    color: colors.textSecondary,
    textAlign: 'center',
  },
});
