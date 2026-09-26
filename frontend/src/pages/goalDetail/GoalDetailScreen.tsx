import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { useRef, useState } from 'react';
import { Alert, Pressable, Text, View } from 'react-native';

import { assetApi, goalApi, safeboxApi } from '@/api';
import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { ScreenState } from '@/components/screenState/ScreenState';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { colors } from '@/styles';
import { formatCurrency } from '@/utils/currency';
import {
  getAccountTransferErrorMessage,
  isFinanceNotConnected,
} from '@/utils/accountTransferError';
import { calculateProgressPercent } from '@/utils/goal';

import { GoalAccountManagementBottomSheet } from './components/GoalAccountManagementBottomSheet';
import { GoalInsightCard } from './components/GoalInsightCard';
import { GoalLinkedAccounts } from './components/GoalLinkedAccounts';
import { GoalProgressChart } from './components/GoalProgressChart';
import { SafeboxCard } from './components/SafeboxCard';
import { GoalSpendingCategories } from './components/GoalSpendingCategories';
import {
  GoalTransferBottomSheet,
  type GoalTransferDirection,
} from './components/GoalTransferBottomSheet';
import { OtherGoalsBottomSheet } from './components/OtherGoalsBottomSheet';
import { useGoalDetail } from './hooks/useGoalDetail';
import { styles } from './GoalDetailScreen.styles';

interface GoalDetailScreenProps {
  goalId: string;
}

type TransferAccount = {
  accountId: number;
  accountName: string;
  accountNoMasked: string;
  balance: number;
};

export function GoalDetailScreen({ goalId }: GoalDetailScreenProps) {
  const router = useRouter();
  const [isOtherGoalsVisible, setIsOtherGoalsVisible] = useState(false);
  const { data, error, isLoading, isRefreshing, refetch } = useGoalDetail(goalId);
  const goal = data?.goal;
  const [draftAccountIds, setDraftAccountIds] = useState<string[]>([]);
  const [isAccountManagementVisible, setIsAccountManagementVisible] =
    useState(false);
  const [isSavingAccounts, setIsSavingAccounts] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const isSavingAccountsRef = useRef(false);
  const isDeletingRef = useRef(false);
  /** 열려 있는 이체 시트. null이면 닫힌 상태다. */
  const [transfer, setTransfer] = useState<{
    direction: GoalTransferDirection;
    goalAccount: TransferAccount;
    withdrawalAvailable?: number;
  } | null>(null);
  const [isTransferring, setIsTransferring] = useState(false);
  const [transferError, setTransferError] = useState<string | null>(null);
  /*
   * 실제 이체라 두 번 누르면 두 번 빠져나간다. 버튼 비활성만으로는 연타를 다 막지 못해
   * 요청 시작 시점에 동기적으로 잠근다(docs/goal-transfer-plan.md §3-4).
   */
  const isTransferringRef = useRef(false);
  const isAllocatingRef = useRef(false);
  const parsedGoalId = Number(goalId);
  const hasValidGoalId = Number.isSafeInteger(parsedGoalId) && parsedGoalId > 0;

  const handleBackPress = () => {
    if (router.canGoBack()) {
      router.back();
      return;
    }

    router.replace(routes.home);
  };

  /**
   * 이체 한 번. 확인을 받은 뒤에만 부른다.
   *
   * 낙관적 업데이트를 하지 않는다 — 실제 이체라 실패할 수 있어, 응답을 받은 뒤에만 화면을 바꾼다.
   * 응답에는 두 계좌 잔액만 오고 목표 진행률은 없어 목표 상세를 다시 조회한다(§3-5).
   */
  const runTransfer = async (
    goalAccount: TransferAccount,
    direction: GoalTransferDirection,
    counterpartAccountId: number,
    amount: number,
  ) => {
    if (isTransferringRef.current) return;

    isTransferringRef.current = true;
    setIsTransferring(true);
    setTransferError(null);

    try {
      await assetApi.transfer(
        direction === 'send'
          ? {
              withdrawalAccountId: counterpartAccountId,
              depositAccountId: goalAccount.accountId,
              amount,
            }
          : {
              withdrawalAccountId: goalAccount.accountId,
              depositAccountId: counterpartAccountId,
              amount,
            },
      );

      setTransfer(null);
      await refetch();
      Alert.alert(
        direction === 'send' ? '보냈어요' : '되가져왔어요',
        `${formatCurrency(amount)}을 옮겼어요.`,
      );
    } catch (transferFailure) {
      // 금융망이 끊겨 있으면 금액을 고쳐도 안 되므로 연결 화면으로 보낸다.
      if (isFinanceNotConnected(transferFailure)) {
        setTransfer(null);
        Alert.alert(
          '금융 정보 연결이 필요해요',
          '연결을 먼저 진행해주세요.',
          [
            { text: '나중에', style: 'cancel' },
            {
              text: '연결하기',
              onPress: () => router.push(routes.financeConnection),
            },
          ],
        );
        return;
      }

      // 그 밖의 실패는 시트를 열어 둔 채 알린다. 금액만 고치면 되는 경우가 많다.
      setTransferError(getAccountTransferErrorMessage(transferFailure));
    } finally {
      isTransferringRef.current = false;
      setIsTransferring(false);
    }
  };

  /**
   * 실제 돈이 움직이므로 확인 없이 실행하지 않는다(§2-4).
   * 되가져오기는 목표 진행률이 내려간다는 것까지 알린다.
   */
  const confirmTransfer = (counterpartAccountId: number, amount: number) => {
    if (transfer === null || goal === undefined) return;

    const { direction, goalAccount } = transfer;
    const isSafeboxTransfer = data?.safeboxAccount?.accountId === goalAccount.accountId;
    const currentPercent = calculateProgressPercent(goal.currentAmount, goal.targetAmount);
    const nextPercent = calculateProgressPercent(
      direction === 'send' ? goal.currentAmount + amount : goal.currentAmount - amount,
      goal.targetAmount,
    );

    Alert.alert(
      direction === 'send'
        ? `${goalAccount.accountName}으로
${formatCurrency(amount)}을 보낼까요?`
        : `${goalAccount.accountName}에서
${formatCurrency(amount)}을 되가져올까요?`,
      isSafeboxTransfer
        ? direction === 'send'
          ? '보낸 돈은 도담 계좌의 미배정 금액으로 들어와요. 목표에 담으면 진행률에 반영돼요.'
          : '목표에 담긴 돈은 그대로예요. 미배정 금액만 이체해요.'
        : direction === 'send'
          ? undefined
          : `${goal.title} 진행률이 ${currentPercent}% → ${nextPercent}%로 내려가요.`,
      [
        { text: '취소', style: 'cancel' },
        {
          text: direction === 'send' ? '보내기' : '되가져오기',
          onPress: () => {
            void runTransfer(goalAccount, direction, counterpartAccountId, amount);
          },
        },
      ],
    );
  };

  const saveLinkedAccounts = async () => {
    if (isSavingAccountsRef.current) return;

    const accountIds = draftAccountIds.map(Number);

    if (
      !hasValidGoalId ||
      accountIds.length > 50 ||
      accountIds.some((accountId) => !Number.isSafeInteger(accountId) || accountId <= 0)
    ) {
      Alert.alert('계좌를 저장하지 못했어요', '계좌 정보를 다시 확인해주세요.');
      return;
    }

    isSavingAccountsRef.current = true;
    setIsSavingAccounts(true);
    try {
      await goalApi.replaceGoalAccounts(parsedGoalId, { accountIds });
      setIsAccountManagementVisible(false);
      await refetch();
    } catch (saveError) {
      Alert.alert(
        '계좌를 저장하지 못했어요',
        saveError instanceof Error
          ? saveError.message
          : '잠시 후 다시 시도해주세요.',
      );
    } finally {
      isSavingAccountsRef.current = false;
      setIsSavingAccounts(false);
    }
  };

  const setSafeboxAmount = async (amount: number) => {
    if (isAllocatingRef.current || !hasValidGoalId) return;
    isAllocatingRef.current = true;
    try {
      await safeboxApi.setGoalAmount(parsedGoalId, amount);
      await refetch();
    } finally {
      isAllocatingRef.current = false;
    }
  };

  const confirmDelete = () => {
    if (isDeletingRef.current || !hasValidGoalId) return;

    Alert.alert('목표를 삭제할까요?', '삭제한 목표는 되돌릴 수 없어요.', [
      { text: '취소', style: 'cancel' },
      {
        text: '삭제',
        style: 'destructive',
        onPress: () => {
          const deleteGoal = async () => {
            if (isDeletingRef.current) return;

            isDeletingRef.current = true;
            setIsDeleting(true);
            try {
              await goalApi.deleteGoal(parsedGoalId);
              router.replace(routes.home);
            } catch (deleteError) {
              Alert.alert(
                '목표를 삭제하지 못했어요',
                deleteError instanceof Error
                  ? deleteError.message
                  : '잠시 후 다시 시도해주세요.',
              );
            } finally {
              isDeletingRef.current = false;
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
      <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
        <AppHeader onBackPress={handleBackPress} showBackButton title="목표 상세" />
        <ScreenState description="목표를 불러오고 있어요" isLoading />
      </SafeAreaLayout>
    );
  }

  if (!goal) {
    return (
      <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
        <AppHeader onBackPress={handleBackPress} showBackButton title="목표 상세" />
        <ScreenState
          description={
            hasValidGoalId
              ? error?.message ?? '네트워크 상태를 확인한 뒤 다시 시도해주세요.'
              : '목표 번호를 확인하거나 이전 화면에서 다시 선택해주세요.'
          }
          onRetry={hasValidGoalId ? () => void refetch() : undefined}
          title={hasValidGoalId ? '목표를 불러오지 못했어요.' : '목표를 찾을 수 없어요.'}
        />
      </SafeAreaLayout>
    );
  }

  const progressPercent = calculateProgressPercent(
    goal.currentAmount,
    goal.targetAmount,
  );
  const progressColor =
    goal.type === 'saving' ? colors.primary.yellow : colors.primary.orange;
  const safeboxAccount = data.safeboxAccount;
  const safeboxGoal = data.safeboxGoal;

  return (
    <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
      <AppHeader
        onBackPress={handleBackPress}
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
        {goal.type === 'saving' && safeboxAccount && safeboxGoal ? (
          <SafeboxCard
            account={safeboxAccount}
            allocation={safeboxGoal}
            otherAccounts={data.linkedAccounts.filter(
              (item) => item.accountId !== safeboxAccount.accountId,
            )}
            onSetAmount={setSafeboxAmount}
            onTransfer={(direction) => {
              const account = data.availableAccounts.find(
                (item) => item.accountId === safeboxAccount.accountId,
              );
              setTransferError(null);
              setTransfer({
                direction,
                goalAccount: {
                  accountId: safeboxAccount.accountId,
                  accountName: account?.accountName ?? '도담 계좌',
                  accountNoMasked: safeboxAccount.accountNoMasked,
                  balance: safeboxAccount.balance,
                },
                withdrawalAvailable: safeboxAccount.availableAmount,
              });
            }}
          />
        ) : goal.type === 'saving' ? (
          <GoalLinkedAccounts
            accounts={data.linkedAccounts}
            onManage={() => {
              setDraftAccountIds(
                data.linkedAccounts.map(({ accountId }) => String(accountId)),
              );
              setIsAccountManagementVisible(true);
            }}
            onSend={(goalAccount) => {
              setTransferError(null);
              setTransfer({ direction: 'send', goalAccount });
            }}
            onWithdraw={(goalAccount) => {
              setTransferError(null);
              setTransfer({ direction: 'withdraw', goalAccount });
            }}
          />
        ) : (
          <GoalSpendingCategories categories={goal.categories} />
        )}
      </RefreshableBouncyScrollView>

      <View style={styles.actions}>
        <Pressable
          accessibilityRole="button"
          accessibilityState={{ disabled: isDeleting || isSavingAccounts }}
          disabled={isDeleting || isSavingAccounts}
          onPress={() => router.push(routes.goalSetupEdit(goal.id))}
          style={({ pressed }) => [
            styles.editButton,
            (isDeleting || isSavingAccounts) && styles.actionDisabled,
            pressed && styles.actionPressed,
          ]}
        >
          <Text style={styles.editButtonLabel}>목표 수정</Text>
        </Pressable>
        <Pressable
          accessibilityRole="button"
          accessibilityState={{ disabled: isDeleting || isSavingAccounts }}
          disabled={isDeleting || isSavingAccounts}
          onPress={() => setIsOtherGoalsVisible(true)}
          style={({ pressed }) => [
            styles.otherGoalsButton,
            (isDeleting || isSavingAccounts) && styles.actionDisabled,
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
        onCurrentGoalDeleted={() => router.replace(routes.home)}
        onSelectGoal={(selectedGoalId) => {
          setIsOtherGoalsVisible(false);
          if (selectedGoalId !== goal.id) {
            router.replace(routes.goalDetail(selectedGoalId));
          }
        }}
        visible={isOtherGoalsVisible}
      />

      {/*
        보내기·되가져오기. 상대편 계좌는 자유입출금 중 목표 통장이 아닌 것만 고를 수 있다.
        해지된 계좌는 이체가 400이라 미리 걸러 둔다(§2-2).
      */}
      {transfer === null ? null : (
        <GoalTransferBottomSheet
          direction={transfer.direction}
          errorMessage={transferError}
          goalAccount={transfer.goalAccount}
          isSafebox={transfer.goalAccount.accountId === safeboxAccount?.accountId}
          withdrawalAvailable={transfer.withdrawalAvailable}
          isSubmitting={isTransferring}
          onClose={() => {
            if (isTransferring) return;
            setTransfer(null);
            setTransferError(null);
          }}
          onSubmit={confirmTransfer}
          selectableAccounts={data.availableAccounts.filter(
            (account) =>
              account.accountType === 'DEMAND_DEPOSIT' &&
              account.status !== 'CLOSED' &&
              account.accountId !== transfer.goalAccount.accountId,
          )}
          visible
        />
      )}

      <GoalAccountManagementBottomSheet
        accounts={data.availableAccounts}
        isSubmitting={isSavingAccounts}
        onClose={() => {
          if (!isSavingAccountsRef.current) setIsAccountManagementVisible(false);
        }}
        onComplete={() => void saveLinkedAccounts()}
        onToggle={(accountId) => {
          if (isSavingAccountsRef.current) return;
          if (draftAccountIds.includes(accountId)) {
            setDraftAccountIds((currentIds) =>
              currentIds.filter((id) => id !== accountId),
            );
            return;
          }
          if (draftAccountIds.length >= 50) {
            Alert.alert(
              '계좌를 더 연결할 수 없어요',
              '계좌는 최대 50개까지 연결할 수 있어요.',
            );
            return;
          }
          setDraftAccountIds((currentIds) => [...currentIds, accountId]);
        }}
        selectedAccountIds={draftAccountIds}
        visible={isAccountManagementVisible}
      />
    </SafeAreaLayout>
  );
}
