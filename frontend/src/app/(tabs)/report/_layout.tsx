import { Stack } from 'expo-router';

import { colors } from '@/styles';

export default function ReportLayout() {
  return (
    <Stack
      screenOptions={{
        contentStyle: { backgroundColor: colors.background },
        headerShown: false,
      }}
    />
  );
}
