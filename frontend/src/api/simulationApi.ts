import type {
  GetSimulationsParams,
  SavingSimulationRequest,
  SimulationApiResponse,
  SimulationComparisonApiResponse,
  SimulationRunApiResponse,
  SimulationSummaryApiResponse,
  SpendingSimulationRequest,
} from '@/types/simulationApi.types';

import { apiClient } from './apiClient';
import type { PageResponse } from './api.types';

const SIMULATION_PATH = '/v1/simulations';

export function runSpendingSimulation(request: SpendingSimulationRequest) {
  return apiClient.post<SimulationRunApiResponse>(`${SIMULATION_PATH}/spending`, {
    body: request,
  });
}

export function runSavingSimulation(request: SavingSimulationRequest) {
  return apiClient.post<SimulationRunApiResponse>(`${SIMULATION_PATH}/saving`, {
    body: request,
  });
}

export function getSimulations({
  type,
  page = 0,
  size = 20,
}: GetSimulationsParams = {}) {
  return apiClient.get<PageResponse<SimulationSummaryApiResponse>>(SIMULATION_PATH, {
    query: { type, page, size },
  });
}

export function getSimulation(simulationId: number) {
  return apiClient.get<SimulationApiResponse>(`${SIMULATION_PATH}/${simulationId}`);
}

export function getSimulationComparison(simulationId: number) {
  return apiClient.get<SimulationComparisonApiResponse>(
    `${SIMULATION_PATH}/${simulationId}/comparison`,
  );
}

export const simulationApi = {
  runSpendingSimulation,
  runSavingSimulation,
  getSimulations,
  getSimulation,
  getSimulationComparison,
};
