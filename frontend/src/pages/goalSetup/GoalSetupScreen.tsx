import { useRouter } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, View } from 'react-native';

import { assetApi, goalApi, isApiError } from '@/api';
import { BottomActionButton } from '@/components/bottomActionButton/BottomActionButton';
import { GoalAccountSelectionList } from '@/components/goalAccountSelectionList/GoalAccountSelectionList';
import { ScreenState } from '@/components/screenState/ScreenState';
import { routes } from '@/constants/routes';
import { AuthScreenLayout } from '@/layout/authScreenLayout/AuthScreenLayout';
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

const MAX_LINKED_ACCOUNTS = 50;

function isValidAmount(value: number) {
  return Number.isSafeInteger(value) && value > 0;
}

function isFutureDate(value: Date | null): value is Date {
  if (!value || Number.isNaN(value.getTime())) return false;

  const selected = new Date(value.getFullYear(), value.getMonth(), value.getDate());
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  return selected.getTime() > today.getTime();
}

function toGoalDateKey(value: Date) {
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`;
}

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
  const [isLoadingAccounts, setIsLoadingAccounts] = useState(true);
  const [accountLoadError, setAccountLoadError] = useState<string | null>(null);
  const submissionInFlightRef = useRef(false);
  const accountRequestIdRef = useRef(0);
  const goalRequestIdRef = useRef(0);
  const parsedEditGoalId = Number(editGoalId);
  const hasValidEditGoalId = !isEditing || (
    Number.isSafeInteger(parsedEditGoalId) && parsedEditGoalId > 0
  );

  const loadAccounts = useCallback(async () => {
    const requestId = ++accountRequestIdRef.current;
    setIsLoadingAccounts(true);
    setAccountLoadError(null);

    try {
      const accounts = await assetApi.getAccounts();
      if (requestId !== accountRequestIdRef.current) return;

      setAvailableAccounts(
        accounts.filter(
          ({ accountType, status }) =>
            accountType !== 'MINUS' && status !== 'CLOSED',
        ),
      );
    } catch {
      if (requestId !== accountRequestIdRef.current) return;
      setAvailableAccounts([]);
      setAccountLoadError('계좌를 불러오지 못했어요. 네트워크 상태를 확인해주세요.');
    } finally {
      if (requestId === accountRequestIdRef.current) setIsLoadingAccounts(false);
    }
  }, []);

  const loadGoal = useCallback(async () => {
    if (!isEditing) return;

    const requestId = ++goalRequestIdRef.current;
    setIsLoadingGoal(true);
    setLoadError(null);

    if (!hasValidEditGoalId) {
      setLoadError('목표 번호를 확인하거나 이전 화면에서 다시 선택해주세요.');
      setIsLoadingGoal(false);
      return;
    }

    try {
      const goal = await goalApi.getGoal(parsedEditGoalId);
      if (requestId !== goalRequestIdRef.current) return;

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
      if (requestId !== goalRequestIdRef.current) return;
      setLoadError(
        error instanceof Error ? error.message : '목표를 불러오지 못했어요.',
      );
    } finally {
      if (requestId === goalRequestIdRef.current) setIsLoadingGoal(false);
    }
  }, [hasValidEditGoalId, isEditing, parsedEditGoalId]);

  useEffect(() => {
    void Promise.resolve().then(loadAccounts);
    return () => {
      accountRequestIdRef.current += 1;
    };
  }, [loadAccounts]);

  useEffect(() => {
    void Promise.resolve().then(loadGoal);
    return () => {
      goalRequestIdRef.current += 1;
    };
  }, [loadGoal]);

  const handleGoalTypeSelect = (type: GoalType) => {
    setSelectedType(type);

    if (type === 'saving') {
      setStep('savingPurpose');
      return;
    }

    setStep('reductionAmount');
  };

  const handleAccountToggle = (accountId: string) => {
    if (submissionInFlightRef.current) return;

    if (selectedAccountIds.includes(accountId)) {
      setSelectedAccountIds((currentIds) =>
        currentIds.filter((id) => id !== accountId),
      );
      return;
    }
    if (selectedAccountIds.length >= MAX_LINKED_ACCOUNTS) {
      Alert.alert(
        '계좌를 더 선택할 수 없어요',
        `계좌는 최대 ${MAX_LINKED_ACCOUNTS}개까지 연결할 수 있어요.`,
      );
      return;
    }
    setSelectedAccountIds((currentIds) => [...currentIds, accountId]);
  };

  const createSpendingGoal = async () => {
    if (submissionInFlightRef.current) return;
    if (!isValidAmount(reductionAmount)) {
      Alert.alert('금액을 확인해주세요', '목표 소비 금액은 1원 이상이어야 해요.');
      return;
    }

    submissionInFlightRef.current = true;
    setIsSubmitting(true);
    try {
      if (isEditing) {
        if (!hasValidEditGoalId) {
          Alert.alert('목표를 수정하지 못했어요', '올바르지 않은 목표입니다.');
          return;
        }
        await goalApi.updateGoal(parsedEditGoalId, {
          monthlyBudget: reductionAmount,
        });
        router.replace(routes.goalDetail(String(parsedEditGoalId)));
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
      submissionInFlightRef.current = false;
      setIsSubmitting(false);
    }
  };

  const createSavingGoal = async () => {
    if (
      submissionInFlightRef.current ||
      !isFutureDate(savingDeadline) ||
      goalName.trim().length === 0 ||
      !isValidAmount(savingAmount) ||
      selectedAccountIds.length === 0 ||
      selectedAccountIds.length > MAX_LINKED_ACCOUNTS
    ) {
      if (!submissionInFlightRef.current) {
        Alert.alert(
          '목표 정보를 확인해주세요',
          !isFutureDate(savingDeadline)
            ? '목표 날짜는 내일 이후로 선택해주세요.'
            : '목표 이름, 금액과 연결 계좌를 다시 확인해주세요.',
        );
      }
      return;
    }

    const accountIds = selectedAccountIds.map(Number);
    if (accountIds.some((accountId) => !Number.isSafeInteger(accountId) || accountId <= 0)) {
      Alert.alert('목표를 만들지 못했어요', '계좌 정보를 다시 확인해주세요.');
      return;
    }

    const targetDate = toGoalDateKey(savingDeadline);

    let goalId = createdSavingGoalId;
    let isLinkingAccounts = false;
    submissionInFlightRef.current = true;
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
      submissionInFlightRef.current = false;
      setIsSubmitting(false);
    }
  };

  const updateSavingGoal = async () => {
    if (submissionInFlightRef.current) return;
    if (
      !hasValidEditGoalId ||
      !isFutureDate(savingDeadline) ||
      goalName.trim().length === 0 ||
      !isValidAmount(savingAmount)
    ) {
      Alert.alert(
        '목표 정보를 확인해주세요',
        !isFutureDate(savingDeadline)
          ? '목표 날짜는 내일 이후로 선택해주세요.'
          : '목표 이름과 금액을 다시 확인해주세요.',
      );
      return;
    }

    submissionInFlightRef.current = true;
    setIsSubmitting(true);
    try {
      await goalApi.updateGoal(parsedEditGoalId, {
        name: goalName.trim(),
        targetAmount: savingAmount,
        targetDate: toGoalDateKey(savingDeadline),
      });
      router.replace(routes.goalDetail(String(parsedEditGoalId)));
    } catch (error) {
      Alert.alert(
        '목표를 수정하지 못했어요',
        error instanceof Error ? error.message : '잠시 후 다시 시도해주세요.',
      );
    } finally {
      submissionInFlightRef.current = false;
      setIsSubmitting(false);
    }
  };

  if (isLoadingGoal || loadError) {
    return (
      <AuthScreenLayout title="목표 수정" showBackButton onBackPress={() => router.back()}>
        <ScreenState
          description={
            isLoadingGoal
              ? '목표 정보를 불러오고 있어요'
              : loadError ?? '목표 정보를 불러오지 못했어요.'
          }
          isLoading={isLoadingGoal}
          onRetry={!isLoadingGoal && hasValidEditGoalId ? () => void loadGoal() : undefined}
          title={!isLoadingGoal ? '목표를 불러오지 못했어요.' : undefined}
        />
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
            disabled={!isValidAmount(savingAmount)}
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
            disabled={!isFutureDate(savingDeadline) || isSubmitting}
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
    const canSelectAccount = !isLoadingAccounts && !accountLoadError && availableAccounts.length > 0;

    return (
      <AuthScreenLayout
        title="어디에 모을까요?"
        description="목표에 사용할 계좌를 선택해주세요."
        headerAccessory={<GoalStepProgress currentStep={4} totalSteps={4} />}
        showBackButton
        onBackPress={() => setStep('savingDeadline')}
        footer={canSelectAccount ? (
          <BottomActionButton
            label={isSubmitting ? '저장 중...' : '선택 완료'}
            disabled={selectedAccountIds.length === 0 || isSubmitting}
            onPress={() => void createSavingGoal()}
          />
        ) : undefined}>
        {isLoadingAccounts ? (
          <ScreenState description="연결할 계좌를 불러오고 있어요" isLoading />
        ) : accountLoadError ? (
          <ScreenState
            description={accountLoadError}
            onRetry={() => void loadAccounts()}
            title="계좌를 불러오지 못했어요."
          />
        ) : availableAccounts.length === 0 ? (
          <ScreenState
            description="입출금 또는 예·적금 계좌를 연결한 뒤 다시 시도해주세요."
            onRetry={() => void loadAccounts()}
            title="선택할 수 있는 계좌가 없어요."
          />
        ) : (
          <GoalAccountSelectionList
            accounts={availableAccounts}
            disabled={isSubmitting}
            selectedAccountIds={selectedAccountIds}
            onToggle={handleAccountToggle}
          />
        )}
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
            disabled={!isValidAmount(reductionAmount) || isSubmitting}
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
