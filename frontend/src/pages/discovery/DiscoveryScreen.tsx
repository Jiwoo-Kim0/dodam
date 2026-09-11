import { useState } from 'react';
import { useRouter } from 'expo-router';

import { MainLayout } from '@/layout/mainLayout/MainLayout';

import { DiscoveryLanding } from './components/DiscoveryLanding';

export function DiscoveryScreen() {
  const router = useRouter();
  const [hasEnteredDiscovery, setHasEnteredDiscovery] = useState(false);

  if (hasEnteredDiscovery) {
    return <MainLayout title="발견" />;
  }

  return (
    <DiscoveryLanding
      onBack={() => router.back()}
      onComplete={() => setHasEnteredDiscovery(true)}
    />
  );
}
