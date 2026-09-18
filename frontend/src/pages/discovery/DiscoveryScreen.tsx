import { useCallback, useState } from 'react';
import { useFocusEffect, useRouter } from 'expo-router';

import { routes } from '@/constants/routes';

import { DiscoveryHome } from './components/DiscoveryHome';
import { DiscoveryLanding } from './components/DiscoveryLanding';
import { useDiscoveryData } from './hooks/useDiscoveryData';

export function DiscoveryScreen() {
  const router = useRouter();
  const [hasEnteredDiscovery, setHasEnteredDiscovery] = useState(false);
  const discovery = useDiscoveryData();
  const { load } = discovery;
  /**
   * 탭을 다시 눌러 돌아왔을 때도 최신 진단을 보여 준다. 예전에는 앱 실행 후 한 번만 조회했다.
   * 최초 진입 때는 훅이 이미 조회를 시작한 상태라, 훅 안의 중복 호출 방지가 두 번 부르는 걸 막는다.
   */
  useFocusEffect(useCallback(() => {
    void load();
  }, [load]));

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
