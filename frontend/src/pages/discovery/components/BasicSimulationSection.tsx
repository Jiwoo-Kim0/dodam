import { Image } from 'expo-image';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, Text, TextInput, View } from 'react-native';

import { colors } from '@/styles';
import { formatNumber } from '@/utils/currency';

import { useBasicSimulation } from '../hooks/useBasicSimulation';
import { styles } from './BasicSimulationSection.styles';
import { SimulationHistorySection } from './SimulationHistorySection';

const DURATION_OPTIONS = [6, 12, 24] as const;

type SupportedSimulationConfig = {
  type: 'SPENDING' | 'SAVING';
  amountLabel: string;
  category?: string;
};

const SIMULATION_CONFIG_BY_INSIGHT: Readonly<
  Record<string, SupportedSimulationConfig>
> = {
  'delivery-dining': {
    type: 'SPENDING',
    amountLabel: '매달 줄일 금액',
    category: 'FOOD',
  },
  'unused-subscription': {
    type: 'SPENDING',
    amountLabel: '매달 줄일 금액',
    category: 'SUBSCRIPTION',
  },
  'idle-funds': {
    type: 'SAVING',
    amountLabel: '변경할 월 저축액',
  },
};

interface BasicSimulationSectionProps {
  insightId: string;
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

export function BasicSimulationSection({ insightId }: BasicSimulationSectionProps) {
  const config = SIMULATION_CONFIG_BY_INSIGHT[insightId];
  const [amountText, setAmountText] = useState('');
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
          <Text style={styles.title}>시뮬레이션 연동 준비 중이에요</Text>
          <Text style={styles.description}>
            저축·예적금 상품·대출·리볼빙·현금서비스·보험 유형은 백엔드 API가
            준비되는 순서대로 연결할 예정이에요.
          </Text>
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
      request: { monthlySavingAmount: amount, durationMonths },
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
