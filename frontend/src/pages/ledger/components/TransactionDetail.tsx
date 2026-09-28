import { useState } from 'react';
import { Pressable, Text, TextInput, View } from 'react-native';

import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { colors } from '@/styles';
import type { LedgerCategory, LedgerTransaction } from '@/types/ledger.types';
import { formatCurrency } from '@/utils/currency';

import { styles } from '../LedgerScreen.styles';
import {
  LedgerHeader,
  TransactionCategoryIcon,
  TransactionRow,
} from './LedgerShared';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const;

function formatDetailDate(transaction: LedgerTransaction) {
  const date = new Date(`${transaction.transactionDate}T00:00:00`);
  return `${date.getFullYear()}년 ${String(date.getMonth() + 1).padStart(2, '0')}월 ${String(
    date.getDate(),
  ).padStart(2, '0')}일 ${transaction.transactionTime}`;
}

function formatHistoryDate(dateValue: string) {
  const date = new Date(`${dateValue}T00:00:00`);
  return `${date.getMonth() + 1}월 ${date.getDate()}일 ${WEEKDAYS[date.getDay()]}요일`;
}

interface TransactionDetailProps {
  transaction: LedgerTransaction;
  merchantHistory: readonly LedgerTransaction[];
  categories: readonly LedgerCategory[];
  onBackPress: () => void;
  onToggleHidden: () => void;
  onOpenCategory: () => void;
  onEditPress: () => void;
  onUpdateMemo: (memo: string) => void;
}

export function TransactionDetail({
  transaction,
  merchantHistory,
  categories,
  onBackPress,
  onToggleHidden,
  onOpenCategory,
  onEditPress,
  onUpdateMemo,
}: TransactionDetailProps) {
  const [isMemoEditing, setIsMemoEditing] = useState(false);
  const [memoDraft, setMemoDraft] = useState(transaction.memo);
  const categoryById = new Map(categories.map((category) => [category.id, category]));
  const isManual = transaction.sourceType === 'manual';
  const category = categoryById.get(transaction.categoryId);
  const historyTotal = merchantHistory.reduce((sum, item) => sum + item.amount, 0);

  const commitMemo = () => {
    onUpdateMemo(memoDraft.trim());
    setIsMemoEditing(false);
  };

  return (
    <View style={styles.fill}>
      <LedgerHeader
        onBackPress={onBackPress}
        onRightPress={onToggleHidden}
        rightLabel={transaction.hidden ? '보이기' : '숨기기'}
        title="상세 내역"
      />

      <RefreshableBouncyScrollView contentContainerStyle={styles.detailContent}>
        <View style={[styles.card, styles.detailCard]}>
          <View style={styles.detailHero}>
            <TransactionCategoryIcon
              categoryCode={transaction.categoryId}
              categoryLabel={category?.label}
            />
            <Text style={styles.detailMerchant}>{transaction.merchantName}</Text>
          </View>

          <Text
            style={[
              styles.detailAmount,
              transaction.transactionType === 'income' && styles.incomeText,
            ]}>
            {transaction.transactionType === 'expense'
              ? `-${formatCurrency(transaction.amount)}`
              : formatCurrency(transaction.amount)}
          </Text>

          <Pressable
            accessibilityRole="button"
            onPress={onOpenCategory}
            style={({ pressed }) => [styles.detailSettingRow, pressed && styles.rowPressed]}>
            <Text style={styles.detailSettingLabel}>카테고리 설정</Text>
            <Text style={styles.detailSettingValue}>
              {categoryById.get(transaction.categoryId)?.label ?? '카테고리 없음'}
            </Text>
            <Text style={styles.rowChevron}>›</Text>
          </Pressable>

          {isManual ? (
            <Pressable
              accessibilityRole="button"
              onPress={onEditPress}
              style={({ pressed }) => [
                styles.detailSettingRow,
                pressed && styles.rowPressed,
              ]}>
              <Text style={styles.detailSettingLabel}>거래 정보 수정</Text>
              <Text style={styles.rowChevron}>›</Text>
            </Pressable>
          ) : null}

          {isMemoEditing ? (
            <View style={styles.detailMemoEditor}>
              <TextInput
                autoFocus
                maxLength={500}
                onBlur={commitMemo}
                onChangeText={setMemoDraft}
                onSubmitEditing={commitMemo}
                placeholder="메모를 남겨보세요"
                placeholderTextColor={colors.textTertiary}
                returnKeyType="done"
                style={styles.detailMemoInput}
                value={memoDraft}
              />
            </View>
          ) : (
            <Pressable
              accessibilityRole="button"
              onPress={() => setIsMemoEditing(true)}
              style={({ pressed }) => [styles.detailSettingRow, pressed && styles.rowPressed]}>
              <Text style={styles.detailSettingLabel}>메모</Text>
              <Text numberOfLines={1} style={styles.detailSettingValue}>
                {transaction.memo || '메모를 남겨보세요'}
              </Text>
              <Text style={styles.rowChevron}>›</Text>
            </Pressable>
          )}

          <View style={styles.divider} />

          <View style={styles.detailInfoRow}>
            <Text style={styles.detailInfoLabel}>결제수단</Text>
            <Text style={styles.detailInfoValue}>{transaction.paymentMethod}</Text>
          </View>
          <View style={styles.detailInfoRow}>
            <Text style={styles.detailInfoLabel}>결제일시</Text>
            <Text style={styles.detailInfoValue}>{formatDetailDate(transaction)}</Text>
          </View>
          <View style={styles.detailInfoRow}>
            <Text style={styles.detailInfoLabel}>승인상태</Text>
            <Text style={styles.detailInfoValue}>{transaction.approvalStatus}</Text>
          </View>
        </View>

        <View style={[styles.card, styles.detailCard]}>
          <Text style={styles.historyTitle}>최근 3개월간 거래 내역</Text>
          <View style={styles.historySummary}>
            <View style={styles.historySummaryItem}>
              <Text style={styles.historySummaryLabel}>거래 횟수</Text>
              <Text style={styles.historySummaryValue}>{`${merchantHistory.length}회`}</Text>
            </View>
            <View style={[styles.historySummaryItem, styles.historySummaryItemBorder]}>
              <Text style={styles.historySummaryLabel}>총 금액</Text>
              <Text style={styles.historySummaryValue}>{`-${formatCurrency(historyTotal)}`}</Text>
            </View>
          </View>

          {merchantHistory.map((historyItem) => (
            <View key={historyItem.id}>
              <Text style={styles.historyDate}>
                {formatHistoryDate(historyItem.transactionDate)}
              </Text>
              <TransactionRow
                amount={historyItem.amount}
                categoryCode={historyItem.categoryId}
                categoryLabel={categoryById.get(historyItem.categoryId)?.label}
                merchantName={historyItem.merchantName}
                transactionType={historyItem.transactionType}
              />
            </View>
          ))}
        </View>
      </RefreshableBouncyScrollView>
    </View>
  );
}
