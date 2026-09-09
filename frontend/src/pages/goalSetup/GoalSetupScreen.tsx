import { useRouter } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';

import { BottomActionButton } from '@/components/bottomActionButton/BottomActionButton';
import { routes } from '@/constants/routes';
import { AuthScreenLayout } from '@/layout/authScreenLayout/AuthScreenLayout';
import { MOCK_GOAL_ACCOUNTS } from '@/mocks/goalAccounts';

import {
  GoalTypeStep,
  type GoalType,
} from './components/GoalTypeStep';
import { GoalStepProgress } from './components/GoalStepProgress';
import { ReductionAmountStep } from './components/ReductionAmountStep';
import { ReductionCompleteStep } from './components/ReductionCompleteStep';
import { SavingAmountStep } from './components/SavingAmountStep';
import { SavingAccountStep } from './components/SavingAccountStep';
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

export function GoalSetupScreen() {
  const router = useRouter();
  const [step, setStep] = useState<GoalSetupStep>('goalType');
  const [selectedType, setSelectedType] = useState<GoalType | null>(null);
  const [goalName, setGoalName] = useState('');
  const [savingAmount, setSavingAmount] = useState(0);
  const [savingDeadline, setSavingDeadline] = useState<Date | null>(null);
  const [selectedAccountIds, setSelectedAccountIds] = useState<string[]>([]);
  const [reductionAmount, setReductionAmount] = useState(0);

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

  if (step === 'savingPurpose') {
    return (
      <AuthScreenLayout
        title="어떤 목표를 만들까요?"
        description="목표 이름을 입력해주세요."
        headerAccessory={<GoalStepProgress currentStep={1} totalSteps={4} />}
        showBackButton
        onBackPress={() => setStep('goalType')}
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
            label="다음"
            disabled={savingDeadline === null}
            onPress={() => setStep('savingAccount')}
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
            label="선택 완료"
            disabled={selectedAccountIds.length === 0}
            onPress={() => setStep('savingComplete')}
          />
        }>
        <SavingAccountStep
          accounts={MOCK_GOAL_ACCOUNTS}
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
        onBackPress={() => setStep('goalType')}
        footer={
          <BottomActionButton
            label="다음"
            disabled={reductionAmount === 0}
            onPress={() => setStep('reductionComplete')}
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
