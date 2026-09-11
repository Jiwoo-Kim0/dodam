import { useLocalSearchParams } from 'expo-router';

import { GoalDetailScreen } from '@/pages/goalDetail/GoalDetailScreen';

export default function GoalDetailRoute() {
  const { id } = useLocalSearchParams<{ id: string | string[] }>();
  const goalId = Array.isArray(id) ? id[0] : id;

  return <GoalDetailScreen goalId={goalId ?? ''} />;
}
