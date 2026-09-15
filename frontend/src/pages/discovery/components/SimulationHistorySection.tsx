import { ActivityIndicator, Pressable, Text, View } from 'react-native';

import { colors } from '@/styles';
import type { SimulationSummaryApiResponse } from '@/types/simulationApi.types';

import { styles } from './SimulationHistorySection.styles';

interface SimulationHistorySectionProps {
  errorMessage: string;
  history: readonly SimulationSummaryApiResponse[];
  isLoading: boolean;
  loadingSimulationId: number | null;
  onRefresh: () => void;
  onSelect: (simulationId: number) => void;
}

function formatCreatedAt(createdAt: string) {
  const date = new Date(createdAt);
  if (Number.isNaN(date.getTime())) return '실행일 확인 불가';

  return `${date.getMonth() + 1}월 ${date.getDate()}일`;
}

export function SimulationHistorySection({
  errorMessage,
  history,
  isLoading,
  loadingSimulationId,
  onRefresh,
  onSelect,
}: SimulationHistorySectionProps) {
  return (
    <View style={styles.section}>
      <View style={styles.header}>
        <Text style={styles.title}>최근 시뮬레이션</Text>
        {!isLoading ? (
          <Pressable
            accessibilityRole="button"
            hitSlop={8}
            onPress={onRefresh}
            style={({ pressed }) => pressed && styles.pressed}>
            <Text style={styles.refreshLabel}>새로고침</Text>
          </Pressable>
        ) : null}
      </View>

      {isLoading ? (
        <View style={styles.stateBox}>
          <ActivityIndicator color={colors.primary.green} />
          <Text style={styles.stateText}>최근 결과를 불러오고 있어요.</Text>
        </View>
      ) : errorMessage ? (
        <View style={styles.stateBox}>
          <Text accessibilityRole="alert" style={styles.errorText}>
            {errorMessage}
          </Text>
          <Pressable
            accessibilityRole="button"
            onPress={onRefresh}
            style={({ pressed }) => [styles.retryButton, pressed && styles.pressed]}>
            <Text style={styles.retryLabel}>다시 시도</Text>
          </Pressable>
        </View>
      ) : history.length === 0 ? (
        <View style={styles.stateBox}>
          <Text style={styles.stateText}>아직 실행한 시뮬레이션이 없어요.</Text>
        </View>
      ) : (
        <View style={styles.list}>
          {history.map((item, index) => {
            const isItemLoading = loadingSimulationId === item.simulationId;

            return (
              <Pressable
                accessibilityRole="button"
                disabled={loadingSimulationId !== null}
                key={item.simulationId}
                onPress={() => onSelect(item.simulationId)}
                style={({ pressed }) => [
                  styles.item,
                  index < history.length - 1 && styles.itemBorder,
                  pressed && styles.pressed,
                ]}>
                <View style={styles.itemCopy}>
                  <Text numberOfLines={1} style={styles.itemTitle}>
                    {item.title}
                  </Text>
                  <Text numberOfLines={1} style={styles.itemSummary}>
                    {formatCreatedAt(item.createdAt)} · {item.summary}
                  </Text>
                </View>
                {isItemLoading ? (
                  <ActivityIndicator color={colors.primary.green} size="small" />
                ) : (
                  <Text style={styles.chevron}>›</Text>
                )}
              </Pressable>
            );
          })}
        </View>
      )}
    </View>
  );
}
