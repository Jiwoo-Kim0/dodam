import { useState } from 'react';
import { Alert, Modal, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import type { SafeboxAccountResponse, SafeboxAllocationResponse } from '@/api/safeboxApi';
import { colors, fontFamily, radius, spacing, typography } from '@/styles';
import type { GoalAccountApiResponse } from '@/types/goalApi.types';
import { formatCurrency } from '@/utils/currency';

type Direction = 'add' | 'remove';

interface SafeboxCardProps {
  account: SafeboxAccountResponse;
  allocation: SafeboxAllocationResponse;
  otherAccounts: readonly GoalAccountApiResponse[];
  onManageDeposits: () => void;
  onTransfer: (direction: 'send' | 'withdraw') => void;
  onSetAmount: (amount: number) => Promise<void>;
}

export function SafeboxCard({
  account,
  allocation,
  otherAccounts,
  onManageDeposits,
  onTransfer,
  onSetAmount,
}: SafeboxCardProps) {
  const [direction, setDirection] = useState<Direction | null>(null);
  const [amountText, setAmountText] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const amount = Number(amountText || '0');
  const maxAmount = direction === 'add' ? account.availableAmount : allocation.allocatedAmount;
  const valid = Number.isSafeInteger(amount) && amount > 0 && amount <= maxAmount;

  const open = (next: Direction) => {
    setAmountText('');
    setDirection(next);
  };

  const submit = () => {
    if (!direction || !valid || isSubmitting) return;
    const nextAmount = direction === 'add'
      ? allocation.allocatedAmount + amount
      : allocation.allocatedAmount - amount;
    const label = direction === 'add' ? '담기' : '꺼내기';
    Alert.alert(
      `${formatCurrency(amount)}을 ${label} 할까요?`,
      direction === 'remove' ? '목표에 모인 금액과 진행률이 내려가요.' : undefined,
      [
        { text: '취소', style: 'cancel' },
        {
          text: label,
          onPress: () => {
            setIsSubmitting(true);
            void onSetAmount(nextAmount)
              .then(() => setDirection(null))
              .catch((error: unknown) => Alert.alert(
                `${label}에 실패했어요`,
                error instanceof Error ? error.message : '잠시 후 다시 시도해 주세요.',
              ))
              .finally(() => setIsSubmitting(false));
          },
        },
      ],
    );
  };

  return (
    <>
      <View style={styles.card}>
        <Text style={styles.title}>도담 계좌 · 목표 세이프박스</Text>
        <Text style={styles.number}>{account.accountNoMasked}</Text>
        <View style={styles.row}>
          <Text style={styles.label}>이 목표에 담긴 돈</Text>
          <Text style={styles.value}>{formatCurrency(allocation.allocatedAmount)}</Text>
        </View>
        <View style={styles.row}>
          <Text style={styles.label}>도담 계좌 잔액</Text>
          <Text style={styles.value}>{formatCurrency(account.balance)}</Text>
        </View>
        <View style={styles.row}>
          <Text style={styles.label}>아직 담지 않은 돈</Text>
          <Text style={styles.value}>{formatCurrency(account.availableAmount)}</Text>
        </View>
        {account.shortfallAmount > 0 ? (
          <Text style={styles.warning}>계좌 잔액이 배정액보다 부족해요. 계좌를 확인해 주세요.</Text>
        ) : null}
        {/* 예·적금은 세이프박스와 별개로 목표에 연결된다. 연결 전에도 통로가 보이도록 늘 둔다. */}
        <View style={styles.otherAccounts}>
          <View style={styles.row}>
            <Text style={styles.label}>함께 연결된 예·적금</Text>
            <Pressable accessibilityRole="button" hitSlop={8} onPress={onManageDeposits} style={styles.linkButton}>
              <Text style={styles.linkText}>{otherAccounts.length > 0 ? '관리' : '+ 연결하기'}</Text>
            </Pressable>
          </View>
          {otherAccounts.map((item) => (
            <View key={item.accountId} style={styles.row}>
              <Text style={styles.label}>{item.accountName}</Text>
              <Text style={styles.value}>{formatCurrency(item.allocatedToThisGoal)}</Text>
            </View>
          ))}
        </View>
        <View style={styles.actions}>
          <Pressable accessibilityRole="button" onPress={() => onTransfer('send')} style={styles.action}>
            <Text style={styles.actionText}>계좌에 보내기</Text>
          </Pressable>
          <Pressable accessibilityRole="button" disabled={account.availableAmount <= 0}
            onPress={() => open('add')} style={styles.action}>
            <Text style={styles.actionText}>목표에 담기</Text>
          </Pressable>
          <Pressable accessibilityRole="button" disabled={allocation.allocatedAmount <= 0}
            onPress={() => open('remove')} style={styles.action}>
            <Text style={styles.actionText}>꺼내기</Text>
          </Pressable>
        </View>
        <Pressable accessibilityRole="button" disabled={account.availableAmount <= 0}
          onPress={() => onTransfer('withdraw')} style={styles.withdrawButton}>
          <Text style={styles.withdrawText}>담지 않은 돈을 주거래 계좌로 되가져오기</Text>
        </Pressable>
      </View>
      <Modal animationType="fade" transparent visible={direction !== null}
        onRequestClose={() => { if (!isSubmitting) setDirection(null); }}>
        <View style={styles.overlay}>
          <Pressable style={styles.backdrop} onPress={() => { if (!isSubmitting) setDirection(null); }} />
          <SafeAreaView edges={['bottom']} style={styles.sheet}>
            <Text style={styles.title}>목표에 {direction === 'add' ? '담기' : '꺼내기'}</Text>
            <Text style={styles.label}>가능한 금액 {formatCurrency(maxAmount)}</Text>
            <TextInput accessibilityLabel="세이프박스 금액" keyboardType="number-pad"
              editable={!isSubmitting} placeholder="금액을 입력해 주세요"
              onChangeText={(value) => setAmountText(value.replace(/\D/g, ''))}
              style={styles.input} value={amountText} />
            <Pressable accessibilityRole="button" disabled={!valid || isSubmitting}
              onPress={submit} style={[styles.submit, (!valid || isSubmitting) && styles.disabled]}>
              <Text style={styles.submitText}>{isSubmitting ? '처리 중...' : direction === 'add' ? '담기' : '꺼내기'}</Text>
            </Pressable>
          </SafeAreaView>
        </View>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  card: { backgroundColor: colors.white, borderRadius: radius.card, padding: spacing[4], marginTop: spacing[4], gap: spacing[2] },
  title: { ...typography.body1, fontFamily: fontFamily.semiBold, color: colors.textPrimary },
  number: { ...typography.caption, color: colors.textTertiary },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  label: { ...typography.body2, color: colors.textSecondary },
  value: { ...typography.body2, fontFamily: fontFamily.semiBold, color: colors.textPrimary },
  warning: { ...typography.caption, color: colors.primary.orange },
  otherAccounts: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.divider, paddingTop: spacing[3], gap: spacing[2] },
  linkButton: { paddingHorizontal: spacing[3], paddingVertical: spacing[2], backgroundColor: colors.primary.lightGreen, borderRadius: radius.default },
  linkText: { ...typography.body2, color: colors.primary.green, fontFamily: fontFamily.semiBold },
  actions: { flexDirection: 'row', gap: spacing[1], marginTop: spacing[2] },
  action: { flex: 1, paddingVertical: spacing[3], alignItems: 'center', backgroundColor: colors.primary.lightGreen, borderRadius: radius.default },
  actionText: { ...typography.caption, color: colors.primary.green, fontFamily: fontFamily.semiBold },
  withdrawButton: { alignItems: 'center', paddingVertical: spacing[2] },
  withdrawText: { ...typography.caption, color: colors.textSecondary },
  overlay: { flex: 1, justifyContent: 'flex-end' },
  backdrop: { ...StyleSheet.absoluteFill, backgroundColor: 'rgba(0,0,0,0.45)' },
  sheet: { backgroundColor: colors.white, borderTopLeftRadius: radius.card, borderTopRightRadius: radius.card, padding: spacing[5], gap: spacing[4] },
  input: { ...typography.body1, borderWidth: 1, borderColor: colors.divider, borderRadius: radius.default, padding: spacing[3], color: colors.textPrimary },
  submit: { backgroundColor: colors.primary.green, borderRadius: radius.default, alignItems: 'center', padding: spacing[4] },
  disabled: { opacity: 0.5 },
  submitText: { ...typography.buttonSmall, color: colors.white },
});
