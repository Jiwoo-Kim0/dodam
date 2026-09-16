import type { PageResponse } from './api.types';
import { apiClient } from './apiClient';
import type {
  DiagnosisApiResponse,
  DiagnosisIssueApiResponse,
  DiagnosisRunApiResponse,
} from '@/types/discoveryApi.types';

const PATH = '/v1/diagnoses';

export const diagnosisApi = {
  getLatest: () => apiClient.get<DiagnosisApiResponse>(`${PATH}/latest`),
  run: (forceRefresh = false) => apiClient.post<DiagnosisRunApiResponse>(PATH, { body: { forceRefresh } }),
  getIssue: (diagnosisId: number, issueId: number) =>
    apiClient.get<DiagnosisIssueApiResponse>(
      `${PATH}/${diagnosisId}/issues/${issueId}`,
    ),
  getIssues: (diagnosisId: number, page = 0) =>
    apiClient.get<PageResponse<DiagnosisIssueApiResponse>>(
      `${PATH}/${diagnosisId}/issues`,
      { query: { page, size: 100 } },
    ),
};
