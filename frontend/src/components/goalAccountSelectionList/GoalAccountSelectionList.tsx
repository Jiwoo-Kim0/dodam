import { Image, Pressable, ScrollView, Text, View } from 'react-native';

import { FINANCIAL_INSTITUTIONS } from '@/mocks/financeConnection';
import type { AccountApiResponse } from '@/types/assetApi.types';

import { styles } from './GoalAccountSelectionList.styles';

interface GoalAccountSelectionListProps {
  accounts: readonly AccountApiResponse[];
  selectedAccountIds: readonly string[];
  onToggle: (accountId: string) => void;
}

export function GoalAccountSelectionList({
  accounts,
  selectedAccountIds,
  onToggle,
}: GoalAccountSelectionListProps) {
  return (
    <View style={styles.container}>
      <Text style={styles.sectionTitle}>연결된 계좌</Text>

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

          return (
            <View
              key={accountId}
              style={[
                styles.accountCard,
                isSelected && styles.selectedAccountCard,
              ]}>
              <Pressable
                onPress={() => onToggle(accountId)}
                accessibilityRole="checkbox"
                accessibilityLabel={`${account.bankName} ${account.accountNoMasked}`}
                accessibilityState={{ checked: isSelected }}
                style={({ pressed }) => [
                  styles.accountRow,
                  pressed && styles.pressedRow,
                ]}>
                {institution ? (
                  <Image
                    source={institution.logo}
                    accessibilityLabel={institution.name}
                    style={styles.bankLogo}
                  />
                ) : null}

                <View style={styles.accountInfo}>
                  <Text style={styles.bankName}>{account.bankName}</Text>
                  <Text style={styles.accountNumber}>
                    {account.accountNoMasked}
                  </Text>
                </View>

                <View
                  style={[
                    styles.checkCircle,
                    isSelected && styles.selectedCheckCircle,
                  ]}>
                  <Text
                    style={[
                      styles.checkmark,
                      isSelected && styles.selectedCheckmark,
                    ]}>
                    ✓
                  </Text>
                </View>
              </Pressable>
            </View>
          );
        })}
      </ScrollView>
    </View>
  );
}
