import { SymbolView } from 'expo-symbols';
import { Modal, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { colors, componentSize } from '@/styles';

import { styles } from './ExistingSpendingGoalBottomSheet.styles';

interface ExistingSpendingGoalBottomSheetProps {
  onClose: () => void;
  onViewGoal: () => void;
  visible: boolean;
}

export function ExistingSpendingGoalBottomSheet({
  onClose,
  onViewGoal,
  visible,
}: ExistingSpendingGoalBottomSheetProps) {
  return (
    <Modal
      animationType="fade"
      onRequestClose={onClose}
      statusBarTranslucent
      transparent
      visible={visible}>
      <View style={styles.overlay}>
        <Pressable
          accessibilityLabel="진행 중인 목표 안내 닫기"
          accessibilityRole="button"
          onPress={onClose}
          style={styles.backdrop}
        />

        <SafeAreaView edges={['bottom']} style={styles.sheet}>
          <View style={styles.handle} />
          <View style={styles.header}>
            <View style={styles.titleGroup}>
              <View style={styles.iconBackground}>
                <SymbolView
                  name={{ android: 'flag', ios: 'flag.fill', web: 'flag' }}
                  size={componentSize.icon.small + 4}
                  tintColor={colors.primary.green}
                />
              </View>
              <Text style={styles.title}>진행 중인 목표가 있어요</Text>
            </View>

            <Pressable
              accessibilityLabel="닫기"
              accessibilityRole="button"
              hitSlop={8}
              onPress={onClose}
              style={({ pressed }) => [styles.closeButton, pressed && styles.pressed]}>
              <SymbolView
                name={{ android: 'close', ios: 'xmark', web: 'close' }}
                size={componentSize.icon.close}
                tintColor={colors.textSecondary}
              />
            </Pressable>
          </View>

          <Text style={styles.description}>
            지출 줄이기 목표는 한 번에 하나만 진행할 수 있어요.
          </Text>

          <View style={styles.actions}>
            <Pressable
              accessibilityRole="button"
              onPress={onViewGoal}
              style={({ pressed }) => [styles.primaryButton, pressed && styles.pressed]}>
              <Text style={styles.primaryButtonLabel}>기존 목표 보기</Text>
            </Pressable>

            <Pressable
              accessibilityRole="button"
              onPress={onClose}
              style={({ pressed }) => [styles.secondaryButton, pressed && styles.pressed]}>
              <Text style={styles.secondaryButtonLabel}>계속 작성</Text>
            </Pressable>
          </View>
        </SafeAreaView>
      </View>
    </Modal>
  );
}
