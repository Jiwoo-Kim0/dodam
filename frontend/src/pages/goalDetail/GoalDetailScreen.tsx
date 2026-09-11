import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';

import { BouncyScrollView } from '@/components/bouncyScrollView/BouncyScrollView';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { findGoalDetail } from '@/mocks/goalDetail';
import { colors } from '@/styles';
import { formatCurrency } from '@/utils/currency';
import { calculateProgressPercent } from '@/utils/goal';

import { GoalAccountManagementBottomSheet } from './components/GoalAccountManagementBottomSheet';
import { GoalInsightCard } from './components/GoalInsightCard';
import { GoalLinkedAccounts } from './components/GoalLinkedAccounts';
import { GoalProgressChart } from './components/GoalProgressChart';
import { GoalSpendingCategories } from './components/GoalSpendingCategories';
import { OtherGoalsBottomSheet } from './components/OtherGoalsBottomSheet';
import { styles } from './GoalDetailScreen.styles';

interface GoalDetailScreenProps {
  goalId: string;
}

export function GoalDetailScreen({ goalId }: GoalDetailScreenProps) {
  const router = useRouter();
  const [isOtherGoalsVisible, setIsOtherGoalsVisible] = useState(false);
  const goal = findGoalDetail(goalId);
  const initialAccountIds =
    goal?.type === 'saving' ? [...goal.linkedAccountIds] : [];
  const [linkedAccountIds, setLinkedAccountIds] =
    useState<string[]>(initialAccountIds);
  const [draftAccountIds, setDraftAccountIds] =
    useState<string[]>(initialAccountIds);
  const [isAccountManagementVisible, setIsAccountManagementVisible] =
    useState(false);

  if (!goal) {
    return (
      <SafeAreaLayout style={styles.screen}>
        <View style={styles.notFound}>
          <Text style={styles.notFoundTitle}>목표를 찾을 수 없어요.</Text>
          <Pressable accessibilityRole="button" onPress={() => router.back()}>
            <Text style={styles.notFoundAction}>이전 화면으로 돌아가기</Text>
          </Pressable>
        </View>
      </SafeAreaLayout>
    );
  }

  const progressPercent = calculateProgressPercent(
    goal.currentAmount,
    goal.targetAmount,
  );
  const progressColor =
    goal.type === 'saving' ? colors.primary.yellow : colors.primary.orange;

  return (
    <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
      <View style={styles.header}>
        <Pressable
          accessibilityLabel="이전 화면으로 이동"
          accessibilityRole="button"
          hitSlop={8}
          onPress={() => router.back()}
          style={({ pressed }) => [
            styles.backButton,
            pressed && styles.backButtonPressed,
          ]}
        >
          <SymbolView
            name={{
              android: 'arrow_back',
              ios: 'chevron.left',
              web: 'arrow_back',
            }}
            size={24}
            tintColor={colors.textPrimary}
          />
        </Pressable>
      </View>

      <BouncyScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.titleRow}>
          <Text style={styles.title}>{goal.title}</Text>
          <Image
            contentFit="contain"
            source={require('@/assets/icons/goal/acorn_icon.png')}
            style={styles.acornIcon}
          />
        </View>
        <Text style={styles.dueDate}>{goal.dueDateLabel}</Text>

        <View style={styles.statusRow}>
          <View style={styles.statusText}>
            <Text style={styles.dDay}>{`D-${goal.dDay}`}</Text>
            {goal.type === 'reducing' ? (
              <Text style={styles.amountLabel}>이번 달 소비 현황</Text>
            ) : null}
            <View style={styles.amountRow}>
              <Text style={[styles.currentAmount, { color: progressColor }]}>
                {formatCurrency(goal.currentAmount)}
              </Text>
              <Text style={styles.targetAmount}>
                {` / ${formatCurrency(goal.targetAmount)}`}
              </Text>
            </View>
          </View>

          <Image
            contentFit="contain"
            source={goal.characterImage}
            style={styles.characterImage}
          />
        </View>

        <View style={styles.progressRow}>
          <View style={styles.progressTrack}>
            <View
              style={[
                styles.progressFill,
                { backgroundColor: progressColor, width: `${progressPercent}%` },
              ]}
            />
          </View>
          <Text style={styles.progressPercent}>{`${progressPercent}%`}</Text>
        </View>

        <GoalInsightCard goal={goal} />
        <GoalProgressChart goal={goal} />
        {goal.type === 'saving' ? (
          <GoalLinkedAccounts
            accountIds={linkedAccountIds}
            onManage={() => {
              setDraftAccountIds(linkedAccountIds);
              setIsAccountManagementVisible(true);
            }}
          />
        ) : (
          <GoalSpendingCategories categories={goal.categories} />
        )}
      </BouncyScrollView>

      <View style={styles.actions}>
        <Pressable
          accessibilityRole="button"
          onPress={() => router.push(routes.goalSetupEdit(goal.id))}
          style={({ pressed }) => [
            styles.editButton,
            pressed && styles.actionPressed,
          ]}
        >
          <Text style={styles.editButtonLabel}>목표 수정</Text>
        </Pressable>
        <Pressable
          accessibilityRole="button"
          onPress={() => setIsOtherGoalsVisible(true)}
          style={({ pressed }) => [
            styles.otherGoalsButton,
            pressed && styles.actionPressed,
          ]}
        >
          <Text style={styles.otherGoalsButtonLabel}>다른 목표 보기</Text>
        </Pressable>
      </View>

      <OtherGoalsBottomSheet
        currentGoalId={goal.id}
        onClose={() => setIsOtherGoalsVisible(false)}
        onCreateGoal={() => {
          setIsOtherGoalsVisible(false);
          router.push(routes.goalSetup);
        }}
        onSelectGoal={(selectedGoalId) => {
          setIsOtherGoalsVisible(false);
          if (selectedGoalId !== goal.id) {
            router.replace(routes.goalDetail(selectedGoalId));
          }
        }}
        visible={isOtherGoalsVisible}
      />

      <GoalAccountManagementBottomSheet
        onClose={() => setIsAccountManagementVisible(false)}
        onComplete={() => {
          setLinkedAccountIds(draftAccountIds);
          setIsAccountManagementVisible(false);
        }}
        onToggle={(accountId) =>
          setDraftAccountIds((currentIds) =>
            currentIds.includes(accountId)
              ? currentIds.filter((id) => id !== accountId)
              : [...currentIds, accountId],
          )
        }
        selectedAccountIds={draftAccountIds}
        visible={isAccountManagementVisible}
      />
    </SafeAreaLayout>
  );
}
