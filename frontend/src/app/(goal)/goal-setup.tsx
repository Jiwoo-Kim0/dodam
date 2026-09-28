import { useLocalSearchParams } from 'expo-router';

import { GoalSetupScreen } from '@/pages/goalSetup/GoalSetupScreen';

export default function GoalSetupRoute() {
  const { goalId, mode } = useLocalSearchParams<{
    goalId?: string | string[];
    mode?: string | string[];
  }>();
  const resolvedGoalId = Array.isArray(goalId) ? goalId[0] : goalId;
  const resolvedMode = Array.isArray(mode) ? mode[0] : mode;

  return (
    <GoalSetupScreen
      editGoalId={resolvedMode === 'edit' ? resolvedGoalId : undefined}
    />
  );
}
