import { Image } from 'expo-image';
import { useRef } from 'react';
import {
  ActivityIndicator,
  Alert,
  Modal,
  Pressable,
  ScrollView,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { goalApi } from '@/api';
import { colors } from '@/styles';
import { formatCurrency } from '@/utils/currency';
import { calculateProgressPercent } from '@/utils/goal';

import { useOtherGoals } from '../hooks/useOtherGoals';
import { styles } from './OtherGoalsBottomSheet.styles';

interface OtherGoalsBottomSheetProps {
  currentGoalId: string;
  visible: boolean;
  onClose: () => void;
  onCreateGoal: () => void;
  onCurrentGoalDeleted: () => void;
  onSelectGoal: (goalId: string) => void;
}

export function OtherGoalsBottomSheet({
  currentGoalId,
  visible,
  onClose,
  onCreateGoal,
  onCurrentGoalDeleted,
  onSelectGoal,
}: OtherGoalsBottomSheetProps) {
  const { error, goals, isLoading, refetch } = useOtherGoals(visible);
  const longPressedGoalIdRef = useRef<string | null>(null);

  const confirmDeleteGoal = (goalId: string, goalTitle: string) => {
    Alert.alert(
      '목표를 삭제할까요?',
      `${goalTitle} 목표는 삭제 후 되돌릴 수 없어요.`,
      [
        { text: '취소', style: 'cancel' },
        {
          text: '삭제',
          style: 'destructive',
          onPress: () => {
            void (async () => {
              try {
                await goalApi.deleteGoal(Number(goalId));
                if (goalId === currentGoalId) {
                  onClose();
                  onCurrentGoalDeleted();
                  return;
                }
                await refetch();
              } catch (deleteError) {
                Alert.alert(
                  '목표를 삭제하지 못했어요',
                  deleteError instanceof Error
                    ? deleteError.message
                    : '잠시 후 다시 시도해주세요.',
                );
              }
            })();
          },
        },
      ],
    );
  };

  return (
    <Modal
      animationType="fade"
      onRequestClose={onClose}
      statusBarTranslucent
      transparent
      visible={visible}
    >
      <View style={styles.overlay}>
        <Pressable
          accessibilityLabel="다른 목표 보기 닫기"
          accessibilityRole="button"
          onPress={onClose}
          style={styles.backdrop}
        />

        <SafeAreaView edges={['bottom']} style={styles.sheet}>
          <View style={styles.header}>
            <Text style={styles.title}>다른 목표 보기</Text>
            <Pressable
              accessibilityLabel="닫기"
              accessibilityRole="button"
              hitSlop={8}
              onPress={onClose}
              style={styles.closeButton}
            >
              <Text style={styles.closeIcon}>×</Text>
            </Pressable>
          </View>

          <View style={styles.tipCard}>
            <View style={styles.tipTextGroup}>
              <Text style={styles.tipTitle}>
                도담의 목표는 모으기와 줄이기 두 가지예요
              </Text>
              <Text style={styles.tipDescription}>
                원하는 목표를 선택하거나 새로 만들어 보세요.
              </Text>
            </View>
            <Image
              contentFit="contain"
              source={require('@/assets/images/home/goal_card_leaf.png')}
              style={styles.tipIcon}
            />
          </View>

          <ScrollView
            contentContainerStyle={styles.goalList}
            showsVerticalScrollIndicator={false}
          >
            {isLoading ? (
              <View style={styles.listState}>
                <ActivityIndicator color={colors.primary.green} />
                <Text style={styles.listStateText}>목표를 불러오고 있어요.</Text>
              </View>
            ) : error ? (
              <View style={styles.listState}>
                <Text style={styles.listStateText}>
                  목표 목록을 불러오지 못했어요.
                </Text>
                <Pressable accessibilityRole="button" onPress={() => void refetch()}>
                  <Text style={styles.retryLabel}>다시 시도</Text>
                </Pressable>
              </View>
            ) : goals.length === 0 ? (
              <View style={styles.listState}>
                <Text style={styles.listStateText}>진행 중인 목표가 없어요.</Text>
              </View>
            ) : null}

            {goals.map((goal) => {
              const isCurrent = goal.id === currentGoalId;
              const progressPercent = calculateProgressPercent(
                goal.currentAmount,
                goal.targetAmount,
              );
              const accentColor =
                goal.type === 'saving'
                  ? colors.primary.yellow
                  : colors.primary.brown;
              const progressColor =
                goal.type === 'saving'
                  ? colors.primary.yellow
                  : colors.primary.orange;

              return (
                <Pressable
                  accessibilityLabel={`${goal.title}, ${formatCurrency(goal.currentAmount)} 중 ${formatCurrency(goal.targetAmount)}, D-${goal.dDay}`}
                  accessibilityRole="button"
                  accessibilityState={{ selected: isCurrent }}
                  key={goal.id}
                  delayLongPress={450}
                  onLongPress={() => {
                    longPressedGoalIdRef.current = goal.id;
                    confirmDeleteGoal(goal.id, goal.title);
                  }}
                  onPress={() => {
                    if (longPressedGoalIdRef.current === goal.id) {
                      longPressedGoalIdRef.current = null;
                      return;
                    }
                    onSelectGoal(goal.id);
                  }}
                  style={({ pressed }) => [
                    styles.goalCard,
                    { borderLeftColor: accentColor },
                    pressed && styles.pressed,
                  ]}
                >
                  <View
                    style={[
                      styles.characterBackground,
                      {
                        backgroundColor:
                          goal.type === 'saving'
                            ? colors.goal.savingCharacterBackground
                            : colors.goal.reducingCharacterBackground,
                      },
                    ]}
                  >
                    <Image
                      contentFit="contain"
                      source={goal.characterImage}
                      style={styles.goalImage}
                    />
                  </View>

                  <View style={styles.goalInformation}>
                    <View style={styles.goalTitleRow}>
                      <Text
                        style={[
                          styles.goalType,
                          {
                            backgroundColor:
                              goal.type === 'saving'
                                ? colors.goal.savingCharacterBackground
                                : colors.goal.reducingCharacterBackground,
                            borderColor: accentColor,
                            color: accentColor,
                          },
                        ]}
                      >
                        {goal.type === 'saving' ? '모으기' : '줄이기'}
                      </Text>
                      <Text style={[styles.goalDDay, { color: accentColor }]}>
                        {`D-${goal.dDay}`}
                      </Text>
                    </View>
                    <Text numberOfLines={1} style={styles.goalTitle}>
                      {goal.title}
                    </Text>
                    <Text style={styles.goalAmount}>
                      {goal.type === 'saving'
                        ? `${formatCurrency(goal.currentAmount)} / ${formatCurrency(goal.targetAmount)}`
                        : `이번 달 절약 목표 ${formatCurrency(goal.targetAmount)}`}
                    </Text>
                    <View style={styles.progressTrack}>
                      <View
                        style={[
                          styles.progressFill,
                          {
                            backgroundColor: progressColor,
                            width: `${progressPercent}%`,
                          },
                        ]}
                      />
                    </View>
                  </View>

                  <Text style={styles.chevron}>›</Text>
                </Pressable>
              );
            })}

            <Pressable
              accessibilityRole="button"
              onPress={onCreateGoal}
              style={({ pressed }) => [
                styles.createButton,
                pressed && styles.pressed,
              ]}
            >
              <View style={styles.createIcon}>
                <Text style={styles.createIconLabel}>+</Text>
              </View>
              <Text style={styles.createLabel}>새 목표 만들기</Text>
            </Pressable>
          </ScrollView>
        </SafeAreaView>
      </View>
    </Modal>
  );
}
