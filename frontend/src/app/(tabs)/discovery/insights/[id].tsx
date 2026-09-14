import { useLocalSearchParams } from 'expo-router';

import { DiscoveryInsightDetailScreen } from '@/pages/discovery/DiscoveryInsightDetailScreen';

export default function DiscoveryInsightDetailRoute() {
  const { id } = useLocalSearchParams<{ id: string }>();

  return <DiscoveryInsightDetailScreen insightId={id} />;
}
