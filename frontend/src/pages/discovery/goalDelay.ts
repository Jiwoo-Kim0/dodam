import type { DiagnosisGoalImpactApiResponse } from '@/types/discoveryApi.types';

export interface GoalDelay {
  name: string;
  days: number;
}

/**
 * 진단 시점 스냅샷의 `daysDifference`(예상 달성일 − 목표일)를 그대로 읽는다. 재계산하지 않는다.
 *
 * <p>이 값은 <b>목표 전체의 현재 지연</b>이지 특정 카테고리 소비 때문에 생긴 지연이 아니다.
 * 그래서 화면 문구도 "이 소비가 계속되면"이 아니라 "지금 속도면"으로 쓴다. 카테고리에 귀속된
 * 일수는 추천의 `advancedDays`가 채워져야 나오며, 지금 소비 증가 생성기는 그 값을 만들지 않는다.
 *
 * <p>목표가 여럿이면 가장 많이 밀린 하나만 고른다. 지연이 없거나(0 이하) 스냅샷에 forecast가 없어
 * 값이 null이면 표시할 것이 없다.
 */
export function mostDelayedGoal(
  impact: DiagnosisGoalImpactApiResponse | null,
): GoalDelay | null {
  if (!impact) return null;

  let worst: GoalDelay | null = null;
  for (const goal of impact.goals) {
    const days = goal.daysDifference;
    const name = goal.name?.trim();
    if (
      !name ||
      typeof days !== 'number' ||
      !Number.isFinite(days) ||
      days <= 0
    ) {
      continue;
    }
    if (!worst || days > worst.days) worst = { name, days: Math.round(days) };
  }
  return worst;
}
