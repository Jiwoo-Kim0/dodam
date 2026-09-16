import type { DiagnosisApiResponse } from '@/types/discoveryApi.types';

const HOUR_MS = 60 * 60 * 1000;
const MINUTE_MS = 60 * 1000;
const KOREA_OFFSET_MS = 9 * HOUR_MS;

export interface DiagnosisCooldown {
  canRun: boolean;
  label: string | null;
}

export function nextDiagnosisAtMillis(value: string): number | null {
  // DiagnosisResponse.completedAt은 Asia/Seoul의 오프셋 없는 LocalDateTime이다.
  const localTime = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?$/.exec(value);
  if (localTime) {
    const [, year, month, day] = localTime;
    return Date.UTC(Number(year), Number(month) - 1, Number(day) + 1) - KOREA_OFFSET_MS;
  }
  const parsed = Date.parse(value);
  if (!Number.isFinite(parsed)) return null;
  const koreanDate = new Date(parsed + KOREA_OFFSET_MS);
  return Date.UTC(
    koreanDate.getUTCFullYear(),
    koreanDate.getUTCMonth(),
    koreanDate.getUTCDate() + 1,
  ) - KOREA_OFFSET_MS;
}

export function diagnosisCooldown(
  diagnosis: DiagnosisApiResponse | null,
  nowMillis: number,
): DiagnosisCooldown {
  if (!diagnosis) return { canRun: true, label: null };
  const nextAvailableAt = nextDiagnosisAtMillis(diagnosis.completedAt);
  if (nextAvailableAt === null) {
    return { canRun: false, label: '가능 시간을 확인할 수 없어요' };
  }

  const remaining = nextAvailableAt - nowMillis;
  if (remaining <= 0) return { canRun: true, label: null };

  const totalMinutes = Math.ceil(remaining / MINUTE_MS);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  const duration = hours > 0
    ? `${hours}시간${minutes > 0 ? ` ${minutes}분` : ''}`
    : `${minutes}분`;
  return { canRun: false, label: `한국 시간 0시까지 ${duration} 남음` };
}
