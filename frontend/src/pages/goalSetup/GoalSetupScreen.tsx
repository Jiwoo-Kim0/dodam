import { useRouter } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, View } from 'react-native';

import { goalApi, safeboxApi } from '@/api';
import { BottomActionButton } from '@/components/bottomActionButton/BottomActionButton';
import { ScreenState } from '@/components/screenState/ScreenState';
import { routes } from '@/constants/routes';
import { AuthScreenLayout } from '@/layout/authScreenLayout/AuthScreenLayout';

import {
  GoalTypeStep,
  type GoalType,
} from './components/GoalTypeStep';
import { ExistingSpendingGoalBottomSheet } from './components/ExistingSpendingGoalBottomSheet';
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
  | 'savingComplete'
  | 'reductionAmount'
  | 'reductionComplete';

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

export function GoalSetupScreen({ editGoalId }: GoalSetupScreenProps) {
  const router = useRouter();
  const isEditing = editGoalId !== undefined;
  const [step, setStep] = useState<GoalSetupStep>('goalType');
  const [selectedType, setSelectedType] = useState<GoalType | null>(null);
  const [goalName, setGoalName] = useState('');
  const [savingAmount, setSavingAmount] = useState(0);
  const [savingDeadline, setSavingDeadline] = useState<Date | null>(null);
  const [reductionAmount, setReductionAmount] = useState(0);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [existingSpendingGoalId, setExistingSpendingGoalId] = useState<number | null>(null);
  const [createdSavingGoalId, setCreatedSavingGoalId] = useState<number | null>(null);
  const [isLoadingGoal, setIsLoadingGoal] = useState(isEditing);
  const [loadError, setLoadError] = useState<string | null>(null);
  const submissionInFlightRef = useRef(false);
  const goalRequestIdRef = useRef(0);
  const parsedEditGoalId = Number(editGoalId);
  const hasValidEditGoalId = !isEditing || (
    Number.isSafeInteger(parsedEditGoalId) && parsedEditGoalId > 0
  );

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

      const existingGoals = await goalApi.getGoals({
        type: 'SPENDING',
        status: 'ACTIVE',
        size: 1,
      });
      const existingGoal = existingGoals.content[0];
      if (existingGoal) {
        setExistingSpendingGoalId(existingGoal.goalId);
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
      !isValidAmount(savingAmount)
    ) {
      if (!submissionInFlightRef.current) {
        Alert.alert(
          '목표 정보를 확인해주세요',
          !isFutureDate(savingDeadline)
            ? '목표 날짜는 내일 이후로 선택해주세요.'
            : '목표 이름과 금액을 다시 확인해주세요.',
        );
      }
      return;
    }

    const targetDate = toGoalDateKey(savingDeadline);

    let goalId = createdSavingGoalId;
    let isLinkingAccounts = false;
    submissionInFlightRef.current = true;
    setIsSubmitting(true);
    try {
      // 전용 계좌는 가입 때 열지만, 금융망 장애로 빠졌으면 여기서 보충한다. 목표 생성으로 잔액을 자동 배정하지 않는다.
      await safeboxApi.openAccount();
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
      await safeboxApi.setGoalAmount(goalId, 0);
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

      Alert.alert(
        isLinkingAccounts
          ? '세이프박스를 준비하지 못했어요'
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
        headerAccessory={<GoalStepProgress currentStep={1} totalSteps={3} />}
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
        headerAccessory={<GoalStepProgress currentStep={2} totalSteps={3} />}
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
        headerAccessory={<GoalStepProgress currentStep={3} totalSteps={3} />}
        showBackButton
        onBackPress={() => setStep('savingAmount')}
        footer={
          <BottomActionButton
            label={isSubmitting ? '저장 중...' : isEditing ? '수정 완료' : '목표 만들기'}
            disabled={!isFutureDate(savingDeadline) || isSubmitting}
            onPress={() =>
              isEditing ? void updateSavingGoal() : void createSavingGoal()
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
      <>
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

        <ExistingSpendingGoalBottomSheet
          onClose={() => setExistingSpendingGoalId(null)}
          onViewGoal={() => {
            if (existingSpendingGoalId === null) return;
            router.replace(routes.goalDetail(String(existingSpendingGoalId)));
          }}
          visible={existingSpendingGoalId !== null}
        />
      </>
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
