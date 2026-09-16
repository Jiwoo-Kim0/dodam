import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, Text, View } from 'react-native';

import { assetApi, goalApi, isApiError } from '@/api';
import { BottomActionButton } from '@/components/bottomActionButton/BottomActionButton';
import { GoalAccountSelectionList } from '@/components/goalAccountSelectionList/GoalAccountSelectionList';
import { routes } from '@/constants/routes';
import { AuthScreenLayout } from '@/layout/authScreenLayout/AuthScreenLayout';
import { colors } from '@/styles';
import type { AccountApiResponse } from '@/types/assetApi.types';

import {
  GoalTypeStep,
  type GoalType,
} from './components/GoalTypeStep';
import { GoalStepProgress } from './components/GoalStepProgress';
import { ReductionAmountStep } from './components/ReductionAmountStep';
import { ReductionCompleteStep } from './components/ReductionCompleteStep';
import { SavingAmountStep } from './components/SavingAmountStep';
import { SavingCompleteStep } from './components/SavingCompleteStep';
import { SavingDeadlineStep } from './components/SavingDeadlineStep';
import { SavingPurposeStep } from './components/SavingPurposeStep';
import { styles } from './GoalSetupScreen.styles';

type GoalSetupStep =
  | 'goalType'
  | 'savingPurpose'
  | 'savingAmount'
  | 'savingDeadline'
  | 'savingAccount'
  | 'savingComplete'
  | 'reductionAmount'
  | 'reductionComplete';

interface GoalSetupScreenProps {
  editGoalId?: string;
}

async function findLinkedGoal(accountIds: readonly number[]) {
  const selectedIds = new Set(accountIds);
  const response = await goalApi.getGoals({ size: 100 });
  const savingGoals = response.content.filter((goal) => goal.type === 'SAVING');
  const goalsWithAccounts = await Promise.all(
    savingGoals.map(async (goal) => ({
      goal,
      accounts: (await goalApi.getGoalAccounts(goal.goalId, { size: 100 })).accounts,
    })),
  );

  return goalsWithAccounts.find(({ accounts }) =>
    accounts.some(({ accountId }) => selectedIds.has(accountId)),
  )?.goal;
}

export function GoalSetupScreen({ editGoalId }: GoalSetupScreenProps) {
  const router = useRouter();
  const isEditing = editGoalId !== undefined;
  const [step, setStep] = useState<GoalSetupStep>('goalType');
  const [selectedType, setSelectedType] = useState<GoalType | null>(null);
  const [goalName, setGoalName] = useState('');
  const [savingAmount, setSavingAmount] = useState(0);
  const [savingDeadline, setSavingDeadline] = useState<Date | null>(null);
  const [selectedAccountIds, setSelectedAccountIds] = useState<string[]>([]);
  const [reductionAmount, setReductionAmount] = useState(0);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [createdSavingGoalId, setCreatedSavingGoalId] = useState<number | null>(null);
  const [isLoadingGoal, setIsLoadingGoal] = useState(isEditing);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [availableAccounts, setAvailableAccounts] = useState<AccountApiResponse[]>([]);

  useEffect(() => {
    const timeout = setTimeout(() => {
      const loadAccounts = async () => {
        try {
          const accounts = await assetApi.getAccounts();
          setAvailableAccounts(
            accounts.filter(
              ({ accountType, status }) =>
                accountType !== 'MINUS' && status !== 'CLOSED',
            ),
          );
        } catch {
          setAvailableAccounts([]);
        }
      };

      void loadAccounts();
    }, 0);

    return () => clearTimeout(timeout);
  }, []);

  useEffect(() => {
    if (!editGoalId) return;

    const timeout = setTimeout(() => {
      const loadGoal = async () => {
        const goalId = Number(editGoalId);
        if (!Number.isSafeInteger(goalId) || goalId <= 0) {
          setLoadError('올바르지 않은 목표입니다.');
          setIsLoadingGoal(false);
          return;
        }

        try {
          const goal = await goalApi.getGoal(goalId);
          setGoalName(goal.name);
          if (goal.type === 'SAVING') {
            setSelectedType('saving');
            setSavingAmount(goal.targetAmount ?? 0);
            setSavingDeadline(
              goal.targetDate ? new Date(`${goal.targetDate}T00:00:00`) : null,
            );
            setStep('savingPurpose');
          } else {
            setSelectedType('spendingReduction');
            setReductionAmount(goal.monthlyBudget ?? 0);
            setStep('reductionAmount');
          }
        } catch (error) {
          setLoadError(
            error instanceof Error ? error.message : '목표를 불러오지 못했어요.',
          );
        } finally {
          setIsLoadingGoal(false);
        }
      };

      void loadGoal();
    }, 0);

    return () => clearTimeout(timeout);
  }, [editGoalId]);

  const handleGoalTypeSelect = (type: GoalType) => {
    setSelectedType(type);

    if (type === 'saving') {
      setStep('savingPurpose');
      return;
    }

    setStep('reductionAmount');
  };

  const handleAccountToggle = (accountId: string) => {
    setSelectedAccountIds((currentIds) =>
      currentIds.includes(accountId)
        ? currentIds.filter((id) => id !== accountId)
        : [...currentIds, accountId],
    );
  };

  const createSpendingGoal = async () => {
    if (reductionAmount <= 0 || isSubmitting) return;

    setIsSubmitting(true);
    try {
      if (isEditing && editGoalId) {
        await goalApi.updateGoal(Number(editGoalId), {
          monthlyBudget: reductionAmount,
        });
        router.replace(routes.goalDetail(editGoalId));
        return;
      }

      await goalApi.createGoal({ name: '월 지출 줄이기', type: 'SPENDING', monthlyBudget: reductionAmount });
      setStep('reductionComplete');
    } catch (error) {
      Alert.alert(
        '목표를 만들지 못했어요',
        error instanceof Error
          ? error.message
          : '잠시 후 다시 시도해주세요.',
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  const createSavingGoal = async () => {
    if (
      !savingDeadline ||
      goalName.trim().length === 0 ||
      savingAmount <= 0 ||
      selectedAccountIds.length === 0 ||
      isSubmitting
    ) {
      return;
    }

    const accountIds = selectedAccountIds.map(Number);
    if (accountIds.some((accountId) => !Number.isSafeInteger(accountId) || accountId <= 0)) {
      Alert.alert('목표를 만들지 못했어요', '계좌 정보를 다시 확인해주세요.');
      return;
    }

    const targetDate = `${savingDeadline.getFullYear()}-${String(savingDeadline.getMonth() + 1).padStart(2, '0')}-${String(savingDeadline.getDate()).padStart(2, '0')}`;

    let goalId = createdSavingGoalId;
    let isLinkingAccounts = false;
    setIsSubmitting(true);
    try {
      if (goalId === null) {
        const createdGoal = await goalApi.createGoal({
          name: goalName.trim(),
          type: 'SAVING',
          targetAmount: savingAmount,
          targetDate,
        });
        goalId = createdGoal.goalId;
        setCreatedSavingGoalId(goalId);
      } else {
        await goalApi.updateGoal(goalId, {
          name: goalName.trim(),
          targetAmount: savingAmount,
          targetDate,
        });
      }

      isLinkingAccounts = true;
      await goalApi.replaceGoalAccounts(goalId, { accountIds });
      setStep('savingComplete');
    } catch (error) {
      const message =
        error instanceof Error ? error.message : '잠시 후 다시 시도해주세요.';
      let rollbackFailed = false;

      if (goalId !== null) {
        try {
          await goalApi.deleteGoal(goalId);
          setCreatedSavingGoalId(null);
        } catch {
          rollbackFailed = true;
        }
      }

      if (
        isLinkingAccounts &&
        isApiError(error) &&
        error.code === 'ACCOUNT_ALREADY_LINKED_TO_GOAL'
      ) {
        let linkedGoal = null;
        try {
          linkedGoal = await findLinkedGoal(accountIds);
        } catch {
          // 연결된 목표 조회 실패는 원래 계좌 충돌 안내를 막지 않는다.
        }

        Alert.alert(
          '이미 다른 목표에 연결된 계좌예요',
          linkedGoal
            ? `이 계좌는 '${linkedGoal.name}' 목표에서 사용하고 있어요.`
            : message,
          [
            { text: '다른 계좌 선택', style: 'cancel' },
            ...(linkedGoal
              ? [{
                  text: '연결된 목표 보기',
                  onPress: () =>
                    router.replace(routes.goalDetail(String(linkedGoal.goalId))),
                }]
              : []),
          ],
        );
        return;
      }

      Alert.alert(
        isLinkingAccounts
          ? '계좌를 연결하지 못했어요'
          : '목표를 만들지 못했어요',
        rollbackFailed
          ? `${message}\n목표만 생성되었어요. 목표 목록에서 삭제해주세요.`
          : message,
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  const updateSavingGoal = async () => {
    if (!editGoalId || !savingDeadline || isSubmitting) return;

    setIsSubmitting(true);
    try {
      await goalApi.updateGoal(Number(editGoalId), {
        name: goalName.trim(),
        targetAmount: savingAmount,
        targetDate: `${savingDeadline.getFullYear()}-${String(savingDeadline.getMonth() + 1).padStart(2, '0')}-${String(savingDeadline.getDate()).padStart(2, '0')}`,
      });
      router.replace(routes.goalDetail(editGoalId));
    } catch (error) {
      Alert.alert(
        '목표를 수정하지 못했어요',
        error instanceof Error ? error.message : '잠시 후 다시 시도해주세요.',
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoadingGoal || loadError) {
    return (
      <AuthScreenLayout title="목표 수정" showBackButton onBackPress={() => router.back()}>
        <View style={styles.statusContent}>
          {isLoadingGoal ? (
            <ActivityIndicator color={colors.primary.green} />
          ) : (
            <Text style={styles.statusText}>{loadError}</Text>
          )}
        </View>
      </AuthScreenLayout>
    );
  }

  if (step === 'savingPurpose') {
    return (
      <AuthScreenLayout
        title="어떤 목표를 만들까요?"
        description="목표 이름을 입력해주세요."
        headerAccessory={<GoalStepProgress currentStep={1} totalSteps={4} />}
        showBackButton
        onBackPress={() =>
          isEditing ? router.back() : setStep('goalType')
        }
        footer={
          <BottomActionButton
            label="다음"
            disabled={goalName.trim().length === 0}
            onPress={() => setStep('savingAmount')}
          />
        }>
        <SavingPurposeStep
          value={goalName}
          onChange={setGoalName}
        />
      </AuthScreenLayout>
    );
  }

  if (step === 'savingAmount') {
    return (
      <AuthScreenLayout
        title="얼마를 모아볼까요?"
        description="목표 금액을 입력해주세요."
        headerAccessory={<GoalStepProgress currentStep={2} totalSteps={4} />}
        showBackButton
        onBackPress={() => setStep('savingPurpose')}
        footer={
          <BottomActionButton
            label="다음"
            disabled={savingAmount === 0}
            onPress={() => setStep('savingDeadline')}
          />
        }>
        <SavingAmountStep
          value={savingAmount}
          onChange={setSavingAmount}
        />
      </AuthScreenLayout>
    );
  }

  if (step === 'savingDeadline') {
    return (
      <AuthScreenLayout
        title="언제까지 모을까요?"
        description="목표 달성 날짜를 선택해주세요."
        headerAccessory={<GoalStepProgress currentStep={3} totalSteps={4} />}
        showBackButton
        onBackPress={() => setStep('savingAmount')}
        footer={
          <BottomActionButton
            label={isSubmitting ? '저장 중...' : isEditing ? '수정 완료' : '다음'}
            disabled={savingDeadline === null || isSubmitting}
            onPress={() =>
              isEditing ? void updateSavingGoal() : setStep('savingAccount')
            }
          />
        }>
        <SavingDeadlineStep
          value={savingDeadline}
          onChange={setSavingDeadline}
        />
      </AuthScreenLayout>
    );
  }

  if (step === 'savingAccount') {
    return (
      <AuthScreenLayout
        title="어디에 모을까요?"
        description="목표에 사용할 계좌를 선택해주세요."
        headerAccessory={<GoalStepProgress currentStep={4} totalSteps={4} />}
        showBackButton
        onBackPress={() => setStep('savingDeadline')}
        footer={
          <BottomActionButton
            label={isSubmitting ? '저장 중...' : '선택 완료'}
            disabled={selectedAccountIds.length === 0 || isSubmitting}
            onPress={() => void createSavingGoal()}
          />
        }>
        <GoalAccountSelectionList
          accounts={availableAccounts}
          selectedAccountIds={selectedAccountIds}
          onToggle={handleAccountToggle}
        />
      </AuthScreenLayout>
    );
  }

  if (step === 'savingComplete' && savingDeadline) {
    return (
      <SavingCompleteStep
        goalName={goalName.trim()}
        savingAmount={savingAmount}
        savingDeadline={savingDeadline}
        onHomePress={() => router.replace(routes.home)}
      />
    );
  }

  if (step === 'reductionAmount') {
    return (
      <AuthScreenLayout
        title="이번달은 얼마를 써볼까요?"
        description="목표 소비금액을 설정해요."
        showBackButton
        onBackPress={() =>
          isEditing ? router.back() : setStep('goalType')
        }
        footer={
          <BottomActionButton
            label={isSubmitting ? '저장 중...' : isEditing ? '수정 완료' : '다음'}
            disabled={reductionAmount === 0 || isSubmitting}
            onPress={() => void createSpendingGoal()}
          />
        }>
        <ReductionAmountStep
          value={reductionAmount}
          onChange={setReductionAmount}
        />
      </AuthScreenLayout>
    );
  }

  if (step === 'reductionComplete') {
    return (
      <ReductionCompleteStep
        targetAmount={reductionAmount}
        onStartPress={() => router.replace(routes.home)}
      />
    );
  }

  return (
    <AuthScreenLayout
      title="목표를 설정하세요"
      description="도담과 함께 이루고 싶은 목표를 골라보세요"
      showBackButton>
      <View style={styles.content}>
        <GoalTypeStep
          selectedType={selectedType}
          onSelect={handleGoalTypeSelect}
        />
      </View>
    </AuthScreenLayout>
  );
}
