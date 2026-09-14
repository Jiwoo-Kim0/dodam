import { useState } from 'react';
import { useRouter } from 'expo-router';

import { routes } from '@/constants/routes';

import { DiscoveryHome } from './components/DiscoveryHome';
import { DiscoveryLanding } from './components/DiscoveryLanding';

export function DiscoveryScreen() {
  const router = useRouter();
  const [hasEnteredDiscovery, setHasEnteredDiscovery] = useState(false);

  if (hasEnteredDiscovery) {
    return (
      <DiscoveryHome
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
