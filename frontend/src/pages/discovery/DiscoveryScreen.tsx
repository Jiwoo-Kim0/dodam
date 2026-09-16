import { useState } from 'react';
import { useRouter } from 'expo-router';

import { routes } from '@/constants/routes';

import { DiscoveryHome } from './components/DiscoveryHome';
import { DiscoveryLanding } from './components/DiscoveryLanding';
import { useDiscoveryData } from './hooks/useDiscoveryData';

export function DiscoveryScreen() {
  const router = useRouter();
  const [hasEnteredDiscovery, setHasEnteredDiscovery] = useState(false);
  const discovery = useDiscoveryData();

  if (hasEnteredDiscovery) {
    return (
      <DiscoveryHome
        {...discovery}
        onBack={() => setHasEnteredDiscovery(false)}
        onInsightPress={(insightId) => router.push(routes.discoveryInsightDetail(insightId))}
        onViewAll={() => router.push(routes.discoveryInsights)}
      />
    );
  }

  return (
    <DiscoveryLanding
      onBack={() => router.back()}
      onComplete={() => setHasEnteredDiscovery(true)}
    />
  );
}
