import { Image } from 'expo-image';
import { useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, Text, TextInput, View } from 'react-native';

import { colors } from '@/styles';
import type { RecommendationSimulationApiResponse } from '@/types/discoveryApi.types';
import type { BasicSimulationType } from '@/types/simulationApi.types';
import { formatNumber } from '@/utils/currency';

import { useBasicSimulation } from '../hooks/useBasicSimulation';
import { styles } from './BasicSimulationSection.styles';
import { SimulationHistorySection } from './SimulationHistorySection';

const DURATION_OPTIONS = [6, 12, 24] as const;

/**
 * 프리셋을 화면이 쓸 수 있는 형태로 옮긴다.
 * 실행 가능한 유형은 SPENDING·SAVING 둘뿐이라 나머지는 null이며 안내 카드가 대신 뜬다.
 */
type RunnableSimulation = {
  type: BasicSimulationType;
  amountLabel: string;
  category?: string;
  goalId?: number;
  initialAmount: number | null;
};

function toRunnable(
  preset: RecommendationSimulationApiResponse | null | undefined,
): RunnableSimulation | null {
  if (!preset?.available || !preset.request) return null;

  if (preset.type === 'SPENDING') {
    return {
      type: 'SPENDING',
      amountLabel: '매달 줄일 금액',
      category: preset.request.category ?? undefined,
      initialAmount: preset.request.monthlyReduction ?? null,
    };
  }

  if (preset.type === 'SAVING') {
    return {
      type: 'SAVING',
      amountLabel: '변경할 월 저축액',
      goalId: preset.request.goalId ?? undefined,
      initialAmount: preset.request.monthlySavingAmount ?? null,
    };
  }

  return null;
}

/** 실행 폼을 띄울 수 없는 이유를 사용자 말로 옮긴다. */
function unavailableMessage(
  preset: RecommendationSimulationApiResponse | null | undefined,
) {
  if (!preset) {
    return '이 발견에는 아직 연결된 추천이 없어 시뮬레이션을 준비하지 못했어요.';
  }

  if (preset.available) {
    // 실행 가능하다고 왔지만 FE가 아직 붙이지 않은 유형이다(대출 갈아타기 등).
    return '이 추천에 맞는 시뮬레이션 화면은 아직 준비 중이에요.';
  }

  switch (preset.unavailableReason) {
    case 'EFFECT_AMOUNT_MISSING':
      return '예상 절감액이 계산되지 않아 시뮬레이션을 준비하지 못했어요.';
    case 'SNAPSHOT_FIELD_MISSING':
      return '계산에 필요한 값이 빠져 있어 시뮬레이션을 준비하지 못했어요.';
    case 'REFINANCE_TARGET_MISSING':
      return '비교할 대출 상품을 찾지 못해 시뮬레이션을 준비하지 못했어요.';
    case 'NO_MATCHING_SIMULATION_TYPE':
    default:
      return '이 추천은 아직 시뮬레이션으로 확인할 수 없어요.';
  }
}

interface BasicSimulationSectionProps {
  /** 우선 추천의 587 프리셋. 없거나 실행 불가면 안내 카드가 뜬다. */
  preset: RecommendationSimulationApiResponse | null;
}

function formatSignedAmount(value: number | null | undefined) {
  if (value === null || value === undefined) return '계산 중';
  const sign = value > 0 ? '+ ' : value < 0 ? '- ' : '';
  return `${sign}${formatNumber(Math.abs(value))}원`;
}

function formatGoalDifference(differenceDays: number | null | undefined) {
  if (differenceDays === null || differenceDays === undefined || differenceDays === 0) {
    return '변화 없음';
  }
  return `${differenceDays > 0 ? '+ ' : '- '}${formatNumber(Math.abs(differenceDays))}일`;
}

function getDurationMonths(request: unknown, fallbackDurationMonths: number) {
  if (!request || typeof request !== 'object' || !('durationMonths' in request)) {
    return fallbackDurationMonths;
  }

  const durationMonths = request.durationMonths;
  return typeof durationMonths === 'number' ? durationMonths : fallbackDurationMonths;
}

export function BasicSimulationSection({ preset }: BasicSimulationSectionProps) {
  // loadHistory 이펙트의 의존성이라 렌더마다 새 객체를 만들면 무한 루프가 된다.
  const config = useMemo(() => toRunnable(preset), [preset]);
  const [amountText, setAmountText] = useState(() => {
    const initial = toRunnable(preset)?.initialAmount;
    // 프리셋이 제안한 금액을 그대로 채워 두고 사용자가 고칠 수 있게 한다.
    return initial ? formatNumber(initial) : '';
  });
  const [durationMonths, setDurationMonths] = useState(12);
  const {
    simulation,
    comparison,
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
  } = useBasicSimulation();

  useEffect(() => {
    if (config) void loadHistory(config.type);
  }, [config, loadHistory]);

  if (!config) {
    return (
      <View style={styles.section}>
        <View style={styles.card}>
          <Text style={styles.title}>바꿔보기는 아직이에요</Text>
          <Text style={styles.description}>{unavailableMessage(preset)}</Text>
          <View style={styles.pendingBadge}>
            <Text style={styles.pendingBadgeText}>후속 유형 연동 대기</Text>
          </View>
        </View>
      </View>
    );
  }

  const amount = amountText
    ? Number(amountText.replace(/[^0-9]/g, ''))
    : Number.NaN;
  const amountError =
    config.type === 'SPENDING'
      ? validationErrors.monthlyReduction
      : validationErrors.monthlySavingAmount;

  const handleAmountChange = (value: string) => {
    const digits = value.replace(/[^0-9]/g, '');
    setAmountText(digits ? formatNumber(Number(digits)) : '');
    reset();
  };

  const handleRun = () => {
    if (config.type === 'SPENDING') {
      void run({
        type: config.type,
        request: {
          category: config.category,
          monthlyReduction: amount,
          durationMonths,
        },
      });
      return;
    }

    void run({
      type: config.type,
      request: { goalId: config.goalId, monthlySavingAmount: amount, durationMonths },
    });
  };

  if (simulation) {
    const cumulativeEffect = comparison?.assets[0]?.delta;
    const goalDifference = simulation.goalImpacts[0]?.differenceDays;
    const resultDurationMonths = getDurationMonths(
      simulation.request,
      durationMonths,
    );
    const effectLabel =
      config.type === 'SPENDING'
        ? `${resultDurationMonths}개월 예상 절감액`
        : `${resultDurationMonths}개월 자산 변화`;

    return (
      <>
        <View style={styles.resultSection}>
          <View style={styles.resultCard}>
            <View style={styles.resultHeader}>
              <Text style={styles.resultTitle}>바꾸면 어떻게 되나요?</Text>
              <Pressable
                accessibilityRole="button"
                hitSlop={8}
                onPress={reset}
                style={({ pressed }) =>
                  pressed && styles.recalculateButtonPressed
                }>
                <Text style={styles.recalculateLabel}>다시 계산</Text>
              </Pressable>
            </View>
            <View style={styles.divider} />

            <View style={styles.resultRow}>
              <Text style={styles.resultLabel}>{effectLabel}</Text>
              <Text
                style={[
                  styles.effectValue,
                  (cumulativeEffect ?? 0) < 0 && styles.decreaseValue,
                ]}>
                {formatSignedAmount(cumulativeEffect)}
              </Text>
            </View>
            <View style={styles.resultRow}>
              <Text style={styles.resultLabel}>모으기 목표 달성일</Text>
              <Text style={styles.goalValue}>
                {formatGoalDifference(goalDifference)}
              </Text>
            </View>

            <View style={styles.noticeBox}>
              <Text style={styles.noticeIcon}>ⓘ</Text>
              <Text style={styles.noticeText}>{simulation.summary}</Text>
            </View>
            {errorMessage ? (
              <Text accessibilityRole="alert" style={styles.errorText}>
                {errorMessage}
              </Text>
            ) : null}
          </View>

          <Image
            accessibilityLabel="시뮬레이션 결과를 안내하는 토리"
            contentFit="contain"
            source={require('@/assets/images/discovery/discovery_detail_result.png')}
            style={styles.resultCharacter}
          />
        </View>
        <SimulationHistorySection
          errorMessage={historyErrorMessage}
          history={history}
          isLoading={isHistoryLoading}
          loadingSimulationId={loadingSimulationId}
          onRefresh={() => void loadHistory(config.type)}
          onSelect={(simulationId) => void load(simulationId)}
        />
      </>
    );
  }

  return (
    <>
      <View style={styles.section}>
        <View style={styles.card}>
          <Text style={styles.title}>직접 바꿔보면?</Text>
          <Text style={styles.description}>
            원하는 금액과 기간을 넣어 실제 금융 데이터를 기준으로 변화를
            확인해보세요.
          </Text>

          <View style={styles.field}>
            <Text style={styles.fieldLabel}>{config.amountLabel}</Text>
            <View style={styles.amountInputRow}>
              <TextInput
                accessibilityLabel={config.amountLabel}
                editable={!isLoading}
                keyboardType="number-pad"
                onChangeText={handleAmountChange}
                placeholder="0"
                placeholderTextColor={colors.textTertiary}
                style={styles.amountInput}
                value={amountText}
              />
              <Text style={styles.amountUnit}>원</Text>
            </View>
            {amountError ? (
              <Text accessibilityRole="alert" style={styles.errorText}>
                {amountError}
              </Text>
            ) : null}
          </View>

          <View style={styles.field}>
            <Text style={styles.fieldLabel}>적용 기간</Text>
            <View style={styles.durationRow}>
              {DURATION_OPTIONS.map((duration) => {
                const isSelected = duration === durationMonths;

                return (
                  <Pressable
                    accessibilityRole="button"
                    accessibilityState={{ selected: isSelected }}
                    disabled={isLoading}
                    key={duration}
                    onPress={() => {
                      setDurationMonths(duration);
                      reset();
                    }}
                    style={[
                      styles.durationButton,
                      isSelected && styles.durationButtonSelected,
                    ]}>
                    <Text
                      style={[
                        styles.durationLabel,
                        isSelected && styles.durationLabelSelected,
                      ]}>
                      {duration}개월
                    </Text>
                  </Pressable>
                );
              })}
            </View>
          </View>

          {errorMessage ? (
            <Text accessibilityRole="alert" style={styles.errorText}>
              {errorMessage}
            </Text>
          ) : null}

          <Pressable
            accessibilityRole="button"
            disabled={isLoading}
            onPress={handleRun}
            style={({ pressed }) => [
              styles.runButton,
              isLoading && styles.runButtonDisabled,
              pressed && !isLoading && styles.runButtonPressed,
            ]}>
            {isLoading ? (
              <ActivityIndicator color={colors.white} />
            ) : (
              <Text style={styles.runButtonLabel}>시뮬레이션 해보기</Text>
            )}
          </Pressable>
        </View>
      </View>
      <SimulationHistorySection
        errorMessage={historyErrorMessage}
        history={history}
        isLoading={isHistoryLoading}
        loadingSimulationId={loadingSimulationId}
        onRefresh={() => void loadHistory(config.type)}
        onSelect={(simulationId) => void load(simulationId)}
      />
    </>
  );
}
