import { Image, Text, View } from 'react-native';

import { BottomActionButton } from '@/components/bottomActionButton/BottomActionButton';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';

import { styles } from './SavingCompleteStep.styles';

interface SavingCompleteStepProps {
  goalName: string;
  savingAmount: number;
  savingDeadline: Date;
  onHomePress: () => void;
}

const formatDate = (date: Date) =>
  `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(
    date.getDate(),
  ).padStart(2, '0')}`;

const getRemainingDays = (deadline: Date) => {
  const today = new Date();
  const targetDate = new Date(deadline);

  today.setHours(0, 0, 0, 0);
  targetDate.setHours(0, 0, 0, 0);

  return Math.max(
    0,
    Math.ceil(
      (targetDate.getTime() - today.getTime()) /
        (1000 * 60 * 60 * 24),
    ),
  );

};

const getMonthlySavingAmount = (amount: number, remainingDays: number) => {
  const remainingMonths = Math.max(Math.ceil(remainingDays / 30), 1);

  return Math.ceil(amount / remainingMonths / 10000) * 10000;
};

export function SavingCompleteStep({
  goalName,
  savingAmount,
  savingDeadline,
  onHomePress,
}: SavingCompleteStepProps) {
  const remainingDays = getRemainingDays(savingDeadline);
  const dDayLabel = remainingDays === 0 ? 'D-DAY' : `D-${remainingDays}`;
  const monthlySavingAmount = getMonthlySavingAmount(
    savingAmount,
    remainingDays,
  );

  return (
    <SafeAreaLayout
      edges={['top', 'left', 'right', 'bottom']}
      style={styles.container}>
      <View style={styles.content}>
        <Image
          source={require('@/assets/characters/dori/dori_goal_setup_complete.png')}
          accessibilityLabel="목표 설정 완료 도리"
          style={styles.character}
        />

        <Text style={styles.title}>목표가 설정되었어요!</Text>

        <View style={styles.summaryCard}>
          <View style={styles.goalHeader}>
            <Image
              source={require('@/assets/icons/goal/acorn_icon.png')}
              accessibilityLabel=""
              style={styles.acornIcon}
            />
            <Text style={styles.goalName}>{goalName}</Text>
          </View>

          <View style={styles.infoRow}>
            <View style={styles.infoLabelGroup}>
              <View style={styles.infoBullet} />
              <Text style={styles.infoLabel}>목표 금액</Text>
            </View>
            <Text style={styles.highlightValue}>
              {savingAmount.toLocaleString('ko-KR')}원
            </Text>
          </View>

          <View style={styles.infoRow}>
            <View style={styles.infoLabelGroup}>
              <View style={styles.infoBullet} />
              <Text style={styles.infoLabel}>목표 날짜</Text>
            </View>
            <Text style={styles.infoValue}>
              {formatDate(savingDeadline)}
            </Text>
          </View>

          <View style={styles.connector}>
            <View style={styles.connectorLine} />
            <View style={styles.connectorDot} />
          </View>

          <View style={styles.dDayBox}>
            <Text style={styles.dDay}>{dDayLabel}</Text>
            <Text style={styles.dDayDescription}>
              매달 약 {monthlySavingAmount.toLocaleString('ko-KR')}원씩 모으면
              달성할 수 있어요.
            </Text>
          </View>
        </View>
      </View>

      <BottomActionButton
        label="홈으로 가기"
        onPress={onHomePress}
      />
    </SafeAreaLayout>
  );
}
