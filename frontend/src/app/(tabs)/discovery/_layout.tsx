import { Stack } from 'expo-router';

import { colors } from '@/styles';

export default function DiscoveryLayout() {
  return (
    <Stack
      screenOptions={{
        contentStyle: { backgroundColor: colors.background },
        headerShown: false,
      }}
    />
  );
}
