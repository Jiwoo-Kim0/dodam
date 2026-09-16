import { useEffect, useState } from 'react';
import { AppState } from 'react-native';

import type { DiagnosisApiResponse } from '@/types/discoveryApi.types';

import { diagnosisCooldown, nextDiagnosisAtMillis } from '../diagnosisCooldown';

export function useDiagnosisCooldown(diagnosis: DiagnosisApiResponse | null) {
  const [nowMillis, setNowMillis] = useState(() => Date.now());
  const nextAvailableAt = diagnosis ? nextDiagnosisAtMillis(diagnosis.completedAt) : null;

  useEffect(() => {
    const timer = setInterval(() => setNowMillis(Date.now()), 60_000);
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') setNowMillis(Date.now());
    });
    return () => { clearInterval(timer); subscription.remove(); };
  }, []);

  useEffect(() => {
    if (nextAvailableAt === null || nextAvailableAt <= Date.now()) return;
    const timer = setTimeout(() => setNowMillis(Date.now()), nextAvailableAt - Date.now() + 50);
    return () => clearTimeout(timer);
  }, [nextAvailableAt]);

  return diagnosisCooldown(diagnosis, nowMillis);
}
