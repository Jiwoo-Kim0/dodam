import { SymbolView } from 'expo-symbols';
import { Image, Pressable, ScrollView, Text, View } from 'react-native';

import { FINANCIAL_INSTITUTIONS } from '@/mocks/financeConnection';
import { colors } from '@/styles';
import type { AccountApiResponse } from '@/types/assetApi.types';
import { formatAccountBalance } from '@/utils/currency';

import { styles } from './GoalAccountSelectionList.styles';

interface GoalAccountSelectionListProps {
  accounts: readonly AccountApiResponse[];
  disabled?: boolean;
  selectedAccountIds: readonly string[];
  onToggle: (accountId: string) => void;
}

export function GoalAccountSelectionList({
  accounts,
  disabled = false,
  selectedAccountIds,
  onToggle,
}: GoalAccountSelectionListProps) {
  return (
    <View style={styles.container}>
      <Text style={styles.sectionTitle}>연결된 계좌</Text>
      {/*
        잔액은 계좌에 들어 있는 돈이지, 이 목표로 모으기로 한 돈이 아니다.
        숫자만 보면 그 둘을 같은 것으로 읽기 쉬워서 목록 위에 한 번 짚어 둔다.
      */}
      <Text style={styles.sectionHint}>
        계좌에 있는 잔액이에요. 목표에 배분되는 금액과는 달라요.
      </Text>

      {accounts.length === 0 ? (
        <Text style={styles.emptyText}>선택할 수 있는 계좌가 없어요.</Text>
      ) : (
        <ScrollView
          showsVerticalScrollIndicator={false}
          contentContainerStyle={styles.accountList}>
          {accounts.map((account) => {
            const institution = FINANCIAL_INSTITUTIONS.find(
              ({ name }) =>
                account.bankName.includes(name.replace('은행', '')) ||
                name.includes(account.bankName.replace('은행', '')),
            );
            const accountId = String(account.accountId);
            const isSelected = selectedAccountIds.includes(accountId);
            const balanceText = formatAccountBalance(
              account.balance,
              account.currency,
            );

            return (
              <Pressable
                key={accountId}
                onPress={() => onToggle(accountId)}
                disabled={disabled}
                accessibilityRole="checkbox"
                accessibilityLabel={`${account.bankName} ${account.accountName} ${account.accountNoMasked}, 잔액 ${balanceText}`}
                accessibilityState={{ checked: isSelected, disabled }}
                style={({ pressed }) => [
                  styles.accountCard,
                  isSelected && styles.selectedAccountCard,
                  pressed && !disabled && styles.pressedCard,
                ]}>
                {/*
                  로고가 없는 은행(테스트 계좌 등)도 있어서, 자리를 비우면 옆 카드와
                  글자 시작점이 어긋난다. 없을 때는 은행 첫 글자로 같은 크기를 채운다.
                */}
                <View style={styles.logoSlot}>
                  {institution ? (
                    <Image
                      source={institution.logo}
                      accessibilityLabel={institution.name}
                      style={styles.bankLogo}
                    />
                  ) : (
                    <View style={styles.logoFallback}>
                      <Text style={styles.logoFallbackText}>
                        {account.bankName.charAt(0)}
                      </Text>
                    </View>
                  )}
                </View>

                <View style={styles.accountInfo}>
                  <Text numberOfLines={1} style={styles.bankName}>
                    {account.bankName}
                  </Text>
                  {/* 계좌명은 은행명과 겹쳐 읽혀서 뺐다. 구분에 필요한 건 번호 쪽이다. */}
                  <Text style={styles.accountNumber}>
                    {account.accountNoMasked}
                  </Text>
                </View>

                {/* 계좌를 고르는 기준이라 체크 옆에 세로 가운데로 세운다. */}
                <Text numberOfLines={1} style={styles.balance}>
                  {balanceText}
                </Text>

                {/* 고르기 전에는 테두리만 둔다. 채운 원을 두면 이미 고른 것처럼 보인다. */}
                <View
                  style={[
                    styles.checkCircle,
                    isSelected && styles.selectedCheckCircle,
                  ]}>
                  {isSelected ? (
                    <SymbolView
                      name={{ android: 'check', ios: 'checkmark', web: 'check' }}
                      size={11}
                      tintColor={colors.white}
                    />
                  ) : null}
                </View>
              </Pressable>
            );
          })}
        </ScrollView>
      )}
    </View>
  );
}
