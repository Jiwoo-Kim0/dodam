import { useCallback, useEffect, useRef, useState } from 'react';

import { isApiError, simulationApi } from '@/api';
import type {
  BasicSimulationApiResponse,
  BasicSimulationType,
  SimulationApiResponse,
  SimulationComparisonApiResponse,
  SimulationSummaryApiResponse,
} from '@/types/simulationApi.types';

import {
  type BasicSimulationInput,
  type BasicSimulationValidationErrors,
  normalizeBasicSimulationInput,
  validateBasicSimulationInput,
} from '../utils/simulationValidation';

interface BasicSimulationState {
  simulation: BasicSimulationApiResponse | SimulationApiResponse | null;
  comparison: SimulationComparisonApiResponse | null;
}

const EMPTY_STATE: BasicSimulationState = {
  simulation: null,
  comparison: null,
};

function getSimulationErrorMessage(error: unknown) {
  if (isApiError(error)) {
    return error.errors[0]?.reason ?? error.message;
  }

  return '시뮬레이션 결과를 불러오지 못했어요. 다시 시도해주세요.';
}

export function useBasicSimulation() {
  const [state, setState] = useState<BasicSimulationState>(EMPTY_STATE);
  const [validationErrors, setValidationErrors] =
    useState<BasicSimulationValidationErrors>({});
  const [errorMessage, setErrorMessage] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [history, setHistory] = useState<readonly SimulationSummaryApiResponse[]>(
    [],
  );
  const [historyErrorMessage, setHistoryErrorMessage] = useState('');
  const [isHistoryLoading, setIsHistoryLoading] = useState(false);
  const [loadingSimulationId, setLoadingSimulationId] = useState<number | null>(null);
  const requestIdRef = useRef(0);
  const historyRequestIdRef = useRef(0);
  const isRequestingRef = useRef(false);

  useEffect(
    () => () => {
      requestIdRef.current += 1;
      historyRequestIdRef.current += 1;
    },
    [],
  );

  const run = useCallback(async (input: BasicSimulationInput) => {
    if (isRequestingRef.current) return null;

    const nextValidationErrors = validateBasicSimulationInput(input);
    setValidationErrors(nextValidationErrors);
    if (Object.keys(nextValidationErrors).length > 0) return null;

    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    isRequestingRef.current = true;
    setErrorMessage('');
    setIsLoading(true);

    try {
      const normalizedInput = normalizeBasicSimulationInput(input);
      const response =
        normalizedInput.type === 'SPENDING'
          ? await simulationApi.runSpendingSimulation(normalizedInput.request)
          : await simulationApi.runSavingSimulation(normalizedInput.request);

      if (requestId !== requestIdRef.current) return null;

      setState({
        simulation: response.simulation,
        comparison: response.simulation.comparison,
      });
      historyRequestIdRef.current += 1;
      setHistoryErrorMessage('');
      setIsHistoryLoading(false);
      setHistory((current) =>
        [
          response.simulation,
          ...current.filter(
            (item) => item.simulationId !== response.simulation.simulationId,
          ),
        ].slice(0, 5),
      );
      return response.simulation;
    } catch (error) {
      if (requestId !== requestIdRef.current) return null;

      setErrorMessage(getSimulationErrorMessage(error));
      return null;
    } finally {
      if (requestId === requestIdRef.current) {
        isRequestingRef.current = false;
        setIsLoading(false);
      }
    }
  }, []);

  const load = useCallback(async (simulationId: number) => {
    if (isRequestingRef.current) return null;

    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    isRequestingRef.current = true;
    setErrorMessage('');
    setValidationErrors({});
    setIsLoading(true);
    setLoadingSimulationId(simulationId);

    try {
      const [simulation, comparison] = await Promise.all([
        simulationApi.getSimulation(simulationId),
        simulationApi.getSimulationComparison(simulationId),
      ]);

      if (requestId !== requestIdRef.current) return null;

      setState({ simulation, comparison });
      return simulation;
    } catch (error) {
      if (requestId !== requestIdRef.current) return null;

      setErrorMessage(getSimulationErrorMessage(error));
      return null;
    } finally {
      if (requestId === requestIdRef.current) {
        isRequestingRef.current = false;
        setIsLoading(false);
        setLoadingSimulationId(null);
      }
    }
  }, []);

  const loadHistory = useCallback(async (type: BasicSimulationType) => {
    const requestId = historyRequestIdRef.current + 1;
    historyRequestIdRef.current = requestId;
    setHistoryErrorMessage('');
    setIsHistoryLoading(true);

    try {
      const response = await simulationApi.getSimulations({
        type,
        page: 0,
        size: 5,
      });
      if (requestId !== historyRequestIdRef.current) return null;

      const completedHistory = response.content.filter(
        (item) => item.status === 'COMPLETED',
      );
      setHistory(completedHistory);
      return completedHistory;
    } catch (error) {
      if (requestId !== historyRequestIdRef.current) return null;

      setHistory([]);
      setHistoryErrorMessage(getSimulationErrorMessage(error));
      return null;
    } finally {
      if (requestId === historyRequestIdRef.current) {
        setIsHistoryLoading(false);
      }
    }
  }, []);

  const reset = useCallback(() => {
    requestIdRef.current += 1;
    isRequestingRef.current = false;
    setState(EMPTY_STATE);
    setValidationErrors({});
    setErrorMessage('');
    setIsLoading(false);
  }, []);

  return {
    ...state,
    validationErrors,
    errorMessage,
    history,
    historyErrorMessage,
    isLoading,
    isHistoryLoading,
    loadingSimulationId,
    run,
    load,
    loadHistory,
    reset,
  };
}
