import { Image } from 'expo-image';
import { Pressable, Text, View } from 'react-native';

import type { GoalAccountApiResponse } from '@/types/goalApi.types';
import { formatCurrency } from '@/utils/currency';
import { findInstitutionByBankName } from '@/utils/institution';

import { styles } from './GoalLinkedAccounts.styles';

/**
 * 목표 통장. 목표에 연결된 자유입출금 계좌이며, 그 잔액이 곧 목표에 모인 금액이다
 * (docs/goal-transfer-plan.md). BE가 목표당 1개만 연결되도록 막으므로 찾으면 하나다.
 */
function findGoalAccount(accounts: readonly GoalAccountApiResponse[]) {
  return accounts.find((account) => account.accountType === 'DEMAND_DEPOSIT') ?? null;
}

interface GoalLinkedAccountsProps {
  accounts: readonly GoalAccountApiResponse[];
  onManage: () => void;
  /** 목표 통장으로 보내기. 목표 통장이 있을 때만 쓰인다. */
  onSend: (goalAccount: GoalAccountApiResponse) => void;
  /** 목표 통장에서 되가져오기. 잔액이 0이면 꺼낼 것이 없어 막는다. */
  onWithdraw: (goalAccount: GoalAccountApiResponse) => void;
}

export function GoalLinkedAccounts({
  accounts,
  onManage,
  onSend,
  onWithdraw,
}: GoalLinkedAccountsProps) {
  const goalAccount = findGoalAccount(accounts);

  return (
    <View style={styles.section}>
      <View style={styles.header}>
        <Text style={styles.title}>{`연결 계좌 (${accounts.length})`}</Text>
        <Pressable
          accessibilityRole="button"
          onPress={onManage}
          style={({ pressed }) => [
            styles.manageButton,
            pressed && styles.manageButtonPressed,
          ]}
        >
          <Text style={styles.manageButtonLabel}>관리</Text>
        </Pressable>
      </View>

      <View style={styles.card}>
        {accounts.map((account, index) => {
          const institution = findInstitutionByBankName(account.bankName);

          return (
            <View
              accessible
              accessibilityLabel={`${account.bankName}, ${account.accountName}, ${formatCurrency(account.balance)}`}
              key={account.accountId}
              style={[
                styles.accountRow,
                index < accounts.length - 1 && styles.accountDivider,
              ]}
            >
              {institution ? (
                <Image
                  accessibilityIgnoresInvertColors
                  contentFit="contain"
                  source={institution.logo}
                  style={styles.logo}
                />
              ) : null}

              <View style={styles.accountInformation}>
                <Text numberOfLines={1} style={styles.bankName}>
                  {account.bankName}
                </Text>
                <Text numberOfLines={1} style={styles.productName}>
                  {account.accountName}
                </Text>
              </View>

              <Text numberOfLines={1} style={styles.amount}>
                {formatCurrency(account.balance)}
              </Text>
            </View>
          );
        })}
      </View>

      {/*
        실제 이체 화면으로 가는 입구다. 목표 통장이 없는 것이 기존 목표의 기본 상태라
        (예·적금만 연결됐거나 아무것도 없다) 그때 무엇을 해야 하는지 먼저 알려 준다.
      */}
      {goalAccount === null ? (
        accounts.length === 0 ? null : (
          <View style={styles.transferNotice}>
            <Text style={styles.transferNoticeText}>
              목표 통장을 연결하면 돈을 모을 수 있어요
            </Text>
            <Pressable
              accessibilityRole="button"
              onPress={onManage}
              style={({ pressed }) => [
                styles.transferNoticeButton,
                pressed && styles.manageButtonPressed,
              ]}>
              <Text style={styles.transferNoticeButtonLabel}>목표 통장 연결하기</Text>
            </Pressable>
          </View>
        )
      ) : (
        <View style={styles.transferActions}>
          <Pressable
            accessibilityHint="다른 통장에서 목표 통장으로 돈을 보내요"
            accessibilityRole="button"
            onPress={() => onSend(goalAccount)}
            style={({ pressed }) => [
              styles.transferButton,
              styles.transferButtonPrimary,
              pressed && styles.manageButtonPressed,
            ]}>
            <Text style={[styles.transferButtonLabel, styles.transferButtonLabelPrimary]}>
              보내기
            </Text>
          </Pressable>

          {/* 목표 통장이 비어 있으면 꺼낼 돈이 없다. */}
          <Pressable
            accessibilityHint="목표 통장에서 다른 통장으로 돈을 되가져와요"
            accessibilityRole="button"
            accessibilityState={{ disabled: goalAccount.balance <= 0 }}
            disabled={goalAccount.balance <= 0}
            onPress={() => onWithdraw(goalAccount)}
            style={({ pressed }) => [
              styles.transferButton,
              goalAccount.balance <= 0 && styles.transferButtonDisabled,
              pressed && goalAccount.balance > 0 && styles.manageButtonPressed,
            ]}>
            <Text
              style={[
                styles.transferButtonLabel,
                goalAccount.balance <= 0 && styles.transferButtonLabelDisabled,
              ]}>
              되가져오기
            </Text>
          </Pressable>
        </View>
      )}
    </View>
  );
}
