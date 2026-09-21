import { useCallback, useRef, useState } from 'react';

import { isApiError, simulationApi } from '@/api';
import type { SimulationApiResponse } from '@/types/simulationApi.types';

/**
 * 채팅 결과 카드의 "자세히 보기". 도구가 만든 시뮬레이션을 그 자리에서 펼쳐 본다.
 *
 * <p>한 번 받아 온 것은 다시 부르지 않는다 — 같은 대화 안에서 접었다 폈다 할 수 있어야 하고,
 * 시뮬레이션 결과는 이미 저장된 값이라 다시 읽을 이유가 없다.
 */
export function useSimulationDetail() {
  const [simulation, setSimulation] = useState<SimulationApiResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const requestIdRef = useRef(0);

  const load = useCallback(
    async (simulationId: number) => {
      if (simulation?.simulationId === simulationId) return;

      const requestId = ++requestIdRef.current;
      setIsLoading(true);
      setError(null);

      try {
        const response = await simulationApi.getSimulation(simulationId);
        if (requestId === requestIdRef.current) setSimulation(response);
      } catch (loadError) {
        if (requestId !== requestIdRef.current) return;
        setSimulation(null);
        setError(
          isApiError(loadError)
            ? loadError.errors[0]?.reason ?? loadError.message
            : '시뮬레이션을 불러오지 못했어요.',
        );
      } finally {
        if (requestId === requestIdRef.current) setIsLoading(false);
      }
    },
    [simulation],
  );

  return { simulation, isLoading, error, load };
}
