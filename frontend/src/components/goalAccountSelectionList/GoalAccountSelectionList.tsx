import { Image, Pressable, ScrollView, Text, View } from 'react-native';

import type { AccountApiResponse } from '@/types/assetApi.types';
import { formatAccountBalance } from '@/utils/currency';
import { findInstitutionByBankName } from '@/utils/institution';

import { styles } from './GoalAccountSelectionList.styles';

/**
 * 예금은 이미 모인 돈만 더하고 적금은 매달 납입액이 저축 속도로 잡혀 예상 달성일이 달라진다.
 * 은행명·번호만으로는 둘을 구분할 수 없어 종류를 함께 보여준다.
 */
const ACCOUNT_TYPE_LABEL: Record<AccountApiResponse['accountType'], string> = {
  DEMAND_DEPOSIT: '입출금',
  MINUS: '마이너스',
  DEPOSIT: '예금',
  SAVINGS: '적금',
};

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
            const institution = findInstitutionByBankName(account.bankName);
            const accountId = String(account.accountId);
            const isSelected = selectedAccountIds.includes(accountId);
            const balanceText = formatAccountBalance(
              account.balance,
              account.currency,
            );
            const typeLabel = ACCOUNT_TYPE_LABEL[account.accountType];

            return (
              <Pressable
                key={accountId}
                onPress={() => onToggle(accountId)}
                disabled={disabled}
                accessibilityRole="checkbox"
                accessibilityLabel={`${account.bankName} ${typeLabel} ${account.accountName} ${account.accountNoMasked}, 잔액 ${balanceText}`}
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
                  <View style={styles.titleRow}>
                    <Text numberOfLines={1} style={styles.bankName}>
                      {account.bankName}
                    </Text>
                    <View style={styles.typeTag}>
                      <Text style={styles.typeTagText}>{typeLabel}</Text>
                    </View>
                  </View>
                  {/* 계좌명은 은행명과 겹쳐 읽혀서 뺐다. 구분에 필요한 건 번호 쪽이다. */}
                  <Text style={styles.accountNumber}>
                    {account.accountNoMasked}
                  </Text>
                </View>

                {/* 계좌를 고르는 기준이라 카드 오른쪽에 세로 가운데로 세운다. */}
                <Text numberOfLines={1} style={styles.balance}>
                  {balanceText}
                </Text>
              </Pressable>
            );
          })}
        </ScrollView>
      )}
    </View>
  );
}
