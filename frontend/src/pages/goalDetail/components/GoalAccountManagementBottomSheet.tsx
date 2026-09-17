import { Modal, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { GoalAccountSelectionList } from '@/components/goalAccountSelectionList/GoalAccountSelectionList';
import type { AccountApiResponse } from '@/types/assetApi.types';

import { styles } from './GoalAccountManagementBottomSheet.styles';

interface GoalAccountManagementBottomSheetProps {
  accounts: readonly AccountApiResponse[];
  isSubmitting: boolean;
  selectedAccountIds: readonly string[];
  visible: boolean;
  onClose: () => void;
  onComplete: () => void;
  onToggle: (accountId: string) => void;
}

export function GoalAccountManagementBottomSheet({
  accounts,
  isSubmitting,
  selectedAccountIds,
  visible,
  onClose,
  onComplete,
  onToggle,
}: GoalAccountManagementBottomSheetProps) {
  return (
    <Modal
      animationType="fade"
      onRequestClose={onClose}
      statusBarTranslucent
      transparent
      visible={visible}
    >
      <View style={styles.overlay}>
        <Pressable
          accessibilityLabel="연결 계좌 관리 닫기"
          accessibilityRole="button"
          accessibilityState={{ disabled: isSubmitting }}
          disabled={isSubmitting}
          onPress={onClose}
          style={styles.backdrop}
        />

        <SafeAreaView edges={['bottom']} style={styles.sheet}>
          <View style={styles.header}>
            <View>
              <Text style={styles.title}>연결 계좌 관리</Text>
              <Text style={styles.description}>
                목표에 연결할 계좌를 선택해 주세요.
              </Text>
            </View>
            <Pressable
              accessibilityLabel="닫기"
              accessibilityRole="button"
              accessibilityState={{ disabled: isSubmitting }}
              disabled={isSubmitting}
              hitSlop={8}
              onPress={onClose}
              style={styles.closeButton}
            >
              <Text style={styles.closeIcon}>×</Text>
            </Pressable>
          </View>

          <View style={styles.accountSelection}>
            <GoalAccountSelectionList
              accounts={accounts}
              disabled={isSubmitting}
              onToggle={onToggle}
              selectedAccountIds={selectedAccountIds}
            />
          </View>

          <Pressable
            accessibilityRole="button"
            accessibilityState={{ disabled: isSubmitting }}
            disabled={isSubmitting}
            onPress={onComplete}
            style={({ pressed }) => [
              styles.completeButton,
              isSubmitting && styles.disabledButton,
              pressed && !isSubmitting && styles.pressedButton,
            ]}
          >
            <Text style={styles.completeButtonLabel}>
              {isSubmitting ? '저장 중...' : '완료'}
            </Text>
          </Pressable>
        </SafeAreaView>
      </View>
    </Modal>
  );
}
