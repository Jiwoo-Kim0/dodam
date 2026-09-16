import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Alert, Pressable, Text, View } from 'react-native';

import { goalApi } from '@/api';
import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { colors } from '@/styles';
import { formatCurrency } from '@/utils/currency';
import { calculateProgressPercent } from '@/utils/goal';

import { GoalAccountManagementBottomSheet } from './components/GoalAccountManagementBottomSheet';
import { GoalInsightCard } from './components/GoalInsightCard';
import { GoalLinkedAccounts } from './components/GoalLinkedAccounts';
import { GoalProgressChart } from './components/GoalProgressChart';
import { GoalSpendingCategories } from './components/GoalSpendingCategories';
import { OtherGoalsBottomSheet } from './components/OtherGoalsBottomSheet';
import { useGoalDetail } from './hooks/useGoalDetail';
import { styles } from './GoalDetailScreen.styles';

interface GoalDetailScreenProps {
  goalId: string;
}

export function GoalDetailScreen({ goalId }: GoalDetailScreenProps) {
  const router = useRouter();
  const [isOtherGoalsVisible, setIsOtherGoalsVisible] = useState(false);
  const { data, error, isLoading, isRefreshing, refetch } = useGoalDetail(goalId);
  const goal = data?.goal;
  const [draftAccountIds, setDraftAccountIds] = useState<string[]>([]);
  const [isAccountManagementVisible, setIsAccountManagementVisible] =
    useState(false);
  const [isDeleting, setIsDeleting] = useState(false);

  const confirmDelete = () => {
    Alert.alert('목표를 삭제할까요?', '삭제한 목표는 되돌릴 수 없어요.', [
      { text: '취소', style: 'cancel' },
      {
        text: '삭제',
        style: 'destructive',
        onPress: () => {
          const deleteGoal = async () => {
            setIsDeleting(true);
            try {
              await goalApi.deleteGoal(Number(goalId));
              router.replace(routes.home);
            } catch (deleteError) {
              Alert.alert(
                '목표를 삭제하지 못했어요',
                deleteError instanceof Error
                  ? deleteError.message
                  : '잠시 후 다시 시도해주세요.',
              );
            } finally {
              setIsDeleting(false);
            }
          };

          void deleteGoal();
        },
      },
    ]);
  };

  if (isLoading) {
    return (
      <SafeAreaLayout style={styles.screen}>
        <View style={styles.notFound}>
          <ActivityIndicator color={colors.primary.green} size="large" />
        </View>
      </SafeAreaLayout>
    );
  }

  if (!goal) {
    return (
      <SafeAreaLayout style={styles.screen}>
        <View style={styles.notFound}>
          <Text style={styles.notFoundTitle}>목표를 불러오지 못했어요.</Text>
          <Text style={styles.notFoundDescription}>
            {error?.message ?? '네트워크 상태를 확인한 뒤 다시 시도해주세요.'}
          </Text>
          <Pressable accessibilityRole="button" onPress={() => void refetch()}>
            <Text style={styles.notFoundAction}>다시 시도</Text>
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
      <AppHeader
        right={
          <Pressable
            accessibilityRole="button"
            disabled={isDeleting}
            hitSlop={8}
            onPress={confirmDelete}
            style={({ pressed }) => [
              styles.headerDeleteButton,
              pressed && styles.actionPressed,
            ]}>
            <Text style={styles.headerDeleteLabel}>
              {isDeleting ? '삭제 중' : '삭제'}
            </Text>
          </Pressable>
        }
        showBackButton
        title={goal.title}
        titleAccessory={
          <Image
            contentFit="contain"
            source={require('@/assets/icons/goal/acorn_icon.png')}
            style={styles.acornIcon}
          />
        }
      />

      <RefreshableBouncyScrollView
        contentContainerStyle={styles.scrollContent}
        onRefresh={() => void refetch()}
        refreshing={isRefreshing}>
        <View style={styles.statusRow}>
          <View style={styles.statusText}>
            <Text style={styles.dueDate}>{goal.dueDateLabel}</Text>
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
        {goal.history.length > 0 ? <GoalProgressChart goal={goal} /> : null}
        {goal.type === 'saving' ? (
          <GoalLinkedAccounts
            accounts={data.linkedAccounts}
            onManage={() => {
              setDraftAccountIds(
                data.linkedAccounts.map(({ accountId }) => String(accountId)),
              );
              setIsAccountManagementVisible(true);
            }}
          />
        ) : (
          <GoalSpendingCategories categories={goal.categories} />
        )}
      </RefreshableBouncyScrollView>

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
        accounts={data.availableAccounts}
        onClose={() => setIsAccountManagementVisible(false)}
        onComplete={() => {
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
