import { useCallback, useEffect, useRef, useState } from 'react';

import { diagnosisApi, isApiError, recommendationApi } from '@/api';
import type { PageResponse } from '@/api/api.types';
import type {
  DiagnosisApiResponse,
  DiagnosisIssueApiResponse,
  RecommendationApiResponse,
} from '@/types/discoveryApi.types';

import { toDiscoveryInsights, type DiscoveryInsight } from '../discoveryModel';
import { diagnosisCooldown } from '../diagnosisCooldown';
import { useDiagnosisCooldown } from './useDiagnosisCooldown';

export async function loadAllPages<T>(fetchPage: (page: number) => Promise<PageResponse<T>>) {
  const first = await fetchPage(0);
  if (first.totalPages <= 1) return first.content;
  const rest = await Promise.all(
    Array.from({ length: first.totalPages - 1 }, (_, index) =>
      fetchPage(index + 1),
    ),
  );
  return [first, ...rest].flatMap((page) => page.content);
}

export function useDiscoveryData() {
  const [diagnosis, setDiagnosis] = useState<DiagnosisApiResponse | null>(null);
  const [insights, setInsights] = useState<DiscoveryInsight[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pendingRecommendationDiagnosisId, setPendingRecommendationDiagnosisId] = useState<number | null>(null);
  const requestIdRef = useRef(0);
  const runningRef = useRef(false);
  const cooldown = useDiagnosisCooldown(diagnosis);

  const load = useCallback(async () => {
    const requestId = ++requestIdRef.current;
    setIsLoading(true);
    setError(null);
    try {
      let latest: DiagnosisApiResponse | null;
      try {
        latest = await diagnosisApi.getLatest();
      } catch (loadError) {
        if (!isApiError(loadError) || loadError.status !== 404) throw loadError;
        latest = null;
      }
      if (!latest) {
        if (requestId === requestIdRef.current) {
          setDiagnosis(null);
          setInsights([]);
        }
        return;
      }
      if (requestId === requestIdRef.current) setDiagnosis(latest);

      const [issues, recommendations] = await Promise.all([
        loadAllPages<DiagnosisIssueApiResponse>((page) =>
          diagnosisApi.getIssues(latest.diagnosisId, page),
        ),
        loadAllPages<RecommendationApiResponse>((page) =>
          recommendationApi.getRecommendations(page),
        ),
      ]);
      if (requestId !== requestIdRef.current) return;
      setDiagnosis(latest);
      // 순위는 연 절감액 내림차순이다. 서버 목록 순서(심각도·생성순)와 다르므로 여기서 다시 세운다.
      // 절감액을 산정하지 못한 문제는 뒤로 보낸다.
      setInsights(
        toDiscoveryInsights(issues, recommendations)
          .sort(
            (left, right) =>
              (right.annualSavingAmount ?? -1) - (left.annualSavingAmount ?? -1),
          )
          .map((insight, index) => ({ ...insight, rank: index + 1 })),
      );
    } catch {
      if (requestId === requestIdRef.current) {
        setInsights([]);
        setError('발견 결과를 불러오지 못했어요. 다시 시도해 주세요.');
      }
    } finally {
      if (requestId === requestIdRef.current) setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void Promise.resolve().then(load);
    return () => { requestIdRef.current += 1; };
  }, [load]);

  const runAnalysis = useCallback(async (forceRefresh = false) => {
    if (runningRef.current) return;
    if (forceRefresh && !diagnosisCooldown(diagnosis, Date.now()).canRun) return;
    runningRef.current = true;
    setIsLoading(true);
    setError(null);
    try {
      if (forceRefresh) {
        let latest: DiagnosisApiResponse | null;
        try {
          latest = await diagnosisApi.getLatest();
        } catch (latestError) {
          if (!isApiError(latestError) || latestError.status !== 404) throw latestError;
          latest = null;
        }
        if (latest && !diagnosisCooldown(latest, Date.now()).canRun) {
          setDiagnosis(latest);
          await load();
          return;
        }
      }
      const result = await diagnosisApi.run(forceRefresh);
      setDiagnosis(result.diagnosis);
      try {
        await recommendationApi.generate(result.diagnosis.diagnosisId);
        setPendingRecommendationDiagnosisId(null);
      } catch {
        setPendingRecommendationDiagnosisId(result.diagnosis.diagnosisId);
        setError('진단은 완료됐지만 추천 생성에 실패했어요. 추천만 다시 시도해 주세요.');
        setIsLoading(false);
        return;
      }
      await load();
    } catch {
      setError('진단을 완료하지 못했어요. 잠시 후 다시 시도해 주세요.');
      setIsLoading(false);
    } finally {
      runningRef.current = false;
    }
  }, [diagnosis, load]);

  const retryRecommendationGeneration = useCallback(async () => {
    if (!pendingRecommendationDiagnosisId || runningRef.current) return;
    runningRef.current = true;
    setIsLoading(true);
    setError(null);
    try {
      await recommendationApi.generate(pendingRecommendationDiagnosisId);
      setPendingRecommendationDiagnosisId(null);
      await load();
    } catch {
      setError('추천을 생성하지 못했어요. 잠시 후 다시 시도해 주세요.');
      setIsLoading(false);
    } finally {
      runningRef.current = false;
    }
  }, [load, pendingRecommendationDiagnosisId]);

  return {
    diagnosis,
    insights,
    isLoading,
    error,
    cooldown,
    canRetryRecommendations: pendingRecommendationDiagnosisId !== null,
    load,
    runAnalysis,
    retryRecommendationGeneration,
  };
}
