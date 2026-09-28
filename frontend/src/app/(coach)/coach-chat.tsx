import { useLocalSearchParams } from 'expo-router';

import { CoachChatScreen } from '@/pages/coachChat/CoachChatScreen';

/** 발견 상세에서 넘어오면 `diagnosisId`·`issueId`가 붙는다. 둘 다 있을 때만 맥락으로 쓴다. */
export default function CoachChatRoute() {
  const { diagnosisId, issueId } = useLocalSearchParams<{
    diagnosisId?: string;
    issueId?: string;
  }>();

  const parsedDiagnosisId = Number(diagnosisId);
  const parsedIssueId = Number(issueId);
  const hasContext = Number.isInteger(parsedDiagnosisId) && Number.isInteger(parsedIssueId);

  return (
    <CoachChatScreen
      context={
        hasContext
          ? { diagnosisId: parsedDiagnosisId, issueId: parsedIssueId }
          : undefined
      }
    />
  );
}
