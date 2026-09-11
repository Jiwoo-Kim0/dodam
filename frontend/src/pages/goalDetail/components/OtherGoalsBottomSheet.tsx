import { Image } from 'expo-image';
import { Modal, Pressable, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { HOME_GOALS } from '@/mocks/home';
import { colors } from '@/styles';
import { formatCurrency } from '@/utils/currency';
import { calculateProgressPercent } from '@/utils/goal';

import { styles } from './OtherGoalsBottomSheet.styles';

interface OtherGoalsBottomSheetProps {
  currentGoalId: string;
  visible: boolean;
  onClose: () => void;
  onCreateGoal: () => void;
  onSelectGoal: (goalId: string) => void;
}

export function OtherGoalsBottomSheet({
  currentGoalId,
  visible,
  onClose,
  onCreateGoal,
  onSelectGoal,
}: OtherGoalsBottomSheetProps) {
  return (
    <Modal
      animationType="slide"
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
            {HOME_GOALS.map((goal) => {
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
                  onPress={() => onSelectGoal(goal.id)}
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
