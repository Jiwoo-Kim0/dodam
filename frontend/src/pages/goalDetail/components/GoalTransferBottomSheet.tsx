import { useMemo, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { colors } from '@/styles';
import type { AccountApiResponse } from '@/types/assetApi.types';
import { formatCurrency, formatNumber } from '@/utils/currency';

import { styles } from './GoalTransferBottomSheet.styles';

/** 보내기는 목표 통장이 받는 곳, 되가져오기는 목표 통장이 보내는 곳이다. */
export type GoalTransferDirection = 'send' | 'withdraw';

/** 빠른 금액. 잔액을 넘는 것은 화면에서 감춘다. */
const QUICK_AMOUNTS = [10_000, 50_000, 100_000] as const;

interface GoalTransferBottomSheetProps {
  /** 도담 계좌 또는 기존 목표 통장. */
  goalAccount: { accountId: number; accountName: string; accountNoMasked: string; balance: number };
  isSafebox?: boolean;
  withdrawalAvailable?: number;
  direction: GoalTransferDirection;
  /** 상대편으로 고를 수 있는 계좌. 목표 통장은 호출부에서 빼고 넘긴다. */
  selectableAccounts: readonly AccountApiResponse[];
  isSubmitting: boolean;
  /** 실패 문구. 시트를 닫지 않고 금액을 고칠 수 있게 그대로 띄운다. */
  errorMessage: string | null;
  visible: boolean;
  onClose: () => void;
  onSubmit: (counterpartAccountId: number, amount: number) => void;
}

function digitsOf(value: string) {
  return value.replace(/[^0-9]/g, '');
}

export function GoalTransferBottomSheet({
  goalAccount,
  isSafebox = false,
  withdrawalAvailable,
  direction,
  selectableAccounts,
  isSubmitting,
  errorMessage,
  visible,
  onClose,
  onSubmit,
}: GoalTransferBottomSheetProps) {
  const isSend = direction === 'send';
  /*
   * 호출부가 시트를 닫을 때 컴포넌트째 내리므로 열 때마다 새로 마운트된다.
   * 덕분에 앞서 치던 금액이 남아 실수로 그대로 보내는 일이 없고, 초기화 이펙트도 필요 없다.
   */
  const [amountText, setAmountText] = useState('');
  const [counterpartId, setCounterpartId] = useState<number | null>(
    () => selectableAccounts[0]?.accountId ?? null,
  );

  const counterpart = useMemo(
    () => selectableAccounts.find((account) => account.accountId === counterpartId) ?? null,
    [counterpartId, selectableAccounts],
  );

  /*
   * 보낼 수 있는 상한은 "돈이 빠져나가는 쪽"의 잔액이다.
   * 보내기는 사용자가 고른 통장, 되가져오기는 목표 통장이다.
   */
  const maxAmount = isSend ? (counterpart?.balance ?? 0) :
    (withdrawalAvailable ?? goalAccount.balance);
  const amount = Number(digitsOf(amountText) || '0');
  const exceedsBalance = amount > maxAmount;
  const canSubmit =
    !isSubmitting && counterpart !== null && amount > 0 && !exceedsBalance;

  const accountLabel = isSafebox ? '도담 계좌' : '목표 통장';
  const title = isSend ? `${accountLabel}에 보내기` : `${accountLabel}에서 되가져오기`;

  return (
    <Modal
      animationType="fade"
      onRequestClose={onClose}
      statusBarTranslucent
      transparent
      visible={visible}>
      <View style={styles.overlay}>
        <Pressable
          accessibilityLabel={`${title} 닫기`}
          accessibilityRole="button"
          accessibilityState={{ disabled: isSubmitting }}
          disabled={isSubmitting}
          onPress={onClose}
          style={styles.backdrop}
        />

        <SafeAreaView edges={['bottom']} style={styles.sheet}>
          <View style={styles.handle} />
          <Text style={styles.title}>{title}</Text>

          <ScrollView keyboardShouldPersistTaps="handled" style={styles.body}>
            {/* 고정된 쪽. 목표당 목표 통장은 하나라 고를 일이 없다. */}
            <Text style={styles.fieldLabel}>{isSend ? '받는 곳' : '보낼 통장'}</Text>
            <View style={styles.fixedAccount}>
              <View style={styles.accountText}>
                <Text numberOfLines={1} style={styles.accountName}>
                  {goalAccount.accountName}
                </Text>
                <Text numberOfLines={1} style={styles.accountNumber}>
                  {goalAccount.accountNoMasked}
                </Text>
              </View>
              <Text style={styles.accountBalance}>{formatCurrency(goalAccount.balance)}</Text>
            </View>

            <Text style={styles.fieldLabel}>{isSend ? '보낼 통장' : '받는 곳'}</Text>
            {selectableAccounts.length === 0 ? (
              <Text style={styles.emptyText}>
                고를 수 있는 통장이 없어요. 금융 정보를 연결한 뒤 다시 시도해주세요.
              </Text>
            ) : (
              <View style={styles.accountOptions}>
                {selectableAccounts.map((account) => {
                  const isSelected = account.accountId === counterpartId;

                  return (
                    <Pressable
                      accessibilityRole="radio"
                      accessibilityState={{ disabled: isSubmitting, selected: isSelected }}
                      disabled={isSubmitting}
                      key={account.accountId}
                      onPress={() => setCounterpartId(account.accountId)}
                      style={({ pressed }) => [
                        styles.accountOption,
                        isSelected && styles.accountOptionSelected,
                        pressed && styles.pressed,
                      ]}>
                      <View style={styles.accountText}>
                        <Text numberOfLines={1} style={styles.accountName}>
                          {account.accountName}
                        </Text>
                        <Text numberOfLines={1} style={styles.accountNumber}>
                          {account.accountNoMasked}
                        </Text>
                      </View>
                      <Text style={styles.accountBalance}>{formatCurrency(account.balance)}</Text>
                    </Pressable>
                  );
                })}
              </View>
            )}

            <Text style={styles.fieldLabel}>금액</Text>
            <View style={[styles.amountRow, exceedsBalance && styles.amountRowError]}>
              <TextInput
                accessibilityLabel="보낼 금액"
                editable={!isSubmitting}
                inputMode="numeric"
                keyboardType="number-pad"
                onChangeText={(next) => setAmountText(digitsOf(next))}
                placeholder="0"
                placeholderTextColor={colors.textTertiary}
                style={styles.amountInput}
                value={amount === 0 ? '' : formatNumber(amount)}
              />
              <Text style={styles.amountUnit}>원</Text>
            </View>

            <Text style={[styles.helperText, exceedsBalance && styles.helperTextError]}>
              {exceedsBalance
                ? `보낼 수 있는 금액은 ${formatCurrency(maxAmount)}까지예요`
                : `최대 ${formatCurrency(maxAmount)}`}
            </Text>

            <View style={styles.quickAmounts}>
              {QUICK_AMOUNTS.filter((quick) => quick <= maxAmount).map((quick) => (
                <Pressable
                  accessibilityRole="button"
                  accessibilityState={{ disabled: isSubmitting }}
                  disabled={isSubmitting}
                  key={quick}
                  onPress={() => setAmountText(String(amount + quick))}
                  style={({ pressed }) => [styles.quickAmount, pressed && styles.pressed]}>
                  <Text style={styles.quickAmountLabel}>{`+${formatNumber(quick / 10_000)}만`}</Text>
                </Pressable>
              ))}
              {maxAmount > 0 ? (
                <Pressable
                  accessibilityRole="button"
                  accessibilityState={{ disabled: isSubmitting }}
                  disabled={isSubmitting}
                  onPress={() => setAmountText(String(maxAmount))}
                  style={({ pressed }) => [styles.quickAmount, pressed && styles.pressed]}>
                  <Text style={styles.quickAmountLabel}>전액</Text>
                </Pressable>
              ) : null}
            </View>

            {errorMessage === null ? null : (
              <Text accessibilityLiveRegion="polite" style={styles.errorText}>
                {errorMessage}
              </Text>
            )}
          </ScrollView>

          <Pressable
            accessibilityRole="button"
            accessibilityState={{ disabled: !canSubmit }}
            disabled={!canSubmit}
            onPress={() => {
              if (counterpart === null) return;
              onSubmit(counterpart.accountId, amount);
            }}
            style={({ pressed }) => [
              styles.submitButton,
              !canSubmit && styles.submitButtonDisabled,
              pressed && canSubmit && styles.pressed,
            ]}>
            {isSubmitting ? (
              <ActivityIndicator color={colors.white} />
            ) : (
              <Text style={styles.submitLabel}>{isSend ? '보내기' : '되가져오기'}</Text>
            )}
          </Pressable>
        </SafeAreaView>
      </View>
    </Modal>
  );
}
