import { Image, Text, View } from 'react-native';

import { BottomActionButton } from '@/components/bottomActionButton/BottomActionButton';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';

import { styles } from './ReductionCompleteStep.styles';

interface ReductionCompleteStepProps {
  targetAmount: number;
  onStartPress: () => void;
}

export function ReductionCompleteStep({
  targetAmount,
  onStartPress,
}: ReductionCompleteStepProps) {
  return (
    <SafeAreaLayout
      edges={['top', 'left', 'right', 'bottom']}
      style={styles.container}>
      <View style={styles.content}>
        <Image
          source={require('@/assets/characters/tori/tori_goal_setup_complete.png')}
          accessibilityLabel="줄이기 목표 설정 완료 토리"
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
            <Text style={styles.goalName}>월 지출 줄이기</Text>
          </View>

          <View style={styles.infoRow}>
            <View style={styles.infoLabelGroup}>
              <View style={styles.infoBullet} />
              <Text style={styles.infoLabel}>목표 금액</Text>
            </View>
            <Text style={styles.highlightValue}>
              {targetAmount.toLocaleString('ko-KR')}원
            </Text>
          </View>
        </View>
      </View>

      <BottomActionButton
        label="홈으로 가기"
        onPress={onStartPress}
      />
    </SafeAreaLayout>
  );
}
