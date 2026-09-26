import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useRef, useState } from 'react';
import { Alert, View } from 'react-native';

import { assetApi } from '@/api';
import type { AccountApiResponse } from '@/types/assetApi.types';
import { formatCurrency } from '@/utils/currency';
import { getAccountTransferErrorMessage, isFinanceNotConnected } from '@/utils/accountTransferError';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { ScreenState } from '@/components/screenState/ScreenState';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';

import { AssetOverviewCard } from './components/AssetOverviewCard';
import { AssetAnalysisCard } from './components/AssetAnalysisCard';
import { AccountTransferBottomSheet } from './components/AccountTransferBottomSheet';
import { AssetSection } from './components/AssetSection';
import { useAssetData } from './hooks/useAssetData';
import { styles } from './AssetsScreen.styles';

export function AssetsScreen() {
  const router = useRouter();
  const {
    analysis,
    cancel,
    error,
    hasAssets,
    isLoading,
    isRefreshing,
    overview,
    refetch,
    reload,
    safeboxAccount,
    sections,
    transferAccounts,
  } = useAssetData();
  const [isTransferOpen, setIsTransferOpen] = useState(false);
  const [isTransferring, setIsTransferring] = useState(false);
  const [transferError, setTransferError] = useState<string | null>(null);
  /** 확인 창을 연속으로 눌러도 한 번만 보내도록 요청 시작 시점에 동기적으로 잠근다. */
  const transferLockRef = useRef(false);

  const accountLabel = (account: AccountApiResponse) =>
    account.accountId === safeboxAccount?.accountId ? '도담 계좌' : account.accountName;

  const runTransfer = async (withdrawal: AccountApiResponse, deposit: AccountApiResponse, amount: number) => {
    if (transferLockRef.current) return;
    transferLockRef.current = true;
    setIsTransferring(true);
    setTransferError(null);
    try {
      await assetApi.transfer({
        withdrawalAccountId: withdrawal.accountId,
        depositAccountId: deposit.accountId,
        amount,
      });
      setIsTransferOpen(false);
      await refetch();
      Alert.alert('보냈어요', `${accountLabel(deposit)}(으)로 ${formatCurrency(amount)}을 옮겼어요.`);
    } catch (failure) {
      if (isFinanceNotConnected(failure)) {
        setIsTransferOpen(false);
        Alert.alert('금융 정보 연결이 필요해요', '연결을 먼저 진행해주세요.', [
          { text: '나중에', style: 'cancel' },
          { text: '연결하기', onPress: () => router.push(routes.financeConnection) },
        ]);
        return;
      }
      setTransferError(getAccountTransferErrorMessage(failure));
    } finally {
      transferLockRef.current = false;
      setIsTransferring(false);
    }
  };

  const confirmTransfer = (withdrawal: AccountApiResponse, deposit: AccountApiResponse, amount: number) => {
    Alert.alert(
      `${formatCurrency(amount)}을 보낼까요?`,
      `${accountLabel(withdrawal)} → ${accountLabel(deposit)}`,
      [
        { text: '취소', style: 'cancel' },
        { text: '보내기', onPress: () => void runTransfer(withdrawal, deposit, amount) },
      ],
    );
  };
  // 탭을 다시 눌러 돌아왔을 때도 최신 자산을 보여 준다. 예전에는 앱 실행 후 한 번만 조회했다.
  useFocusEffect(useCallback(() => {
    void reload();
    return cancel;
  }, [cancel, reload]));

  if (isLoading || error || !overview) {
    return (
      <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
        <AppHeader showBackButton title="내 자산" />

        <ScreenState
          description={
            error
              ? __DEV__
                ? error.message
                : '네트워크 상태를 확인한 뒤 다시 시도해주세요.'
              : '자산을 불러오고 있어요'
          }
          isLoading={!error}
          onRetry={error ? () => void refetch() : undefined}
          title={error ? '자산을 불러오지 못했어요.' : undefined}
        />
      </SafeAreaLayout>
    );
  }

  if (!hasAssets) {
    return (
      <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
        <AppHeader showBackButton title="내 자산" />
        <ScreenState
          actionLabel="금융 연결하기"
          description="금융기관을 연결하면 계좌·예적금·대출·카드 내역을 확인할 수 있어요."
          onAction={() => router.push(routes.financeConnection)}
          title="연결된 자산이 없어요."
        />
      </SafeAreaLayout>
    );
  }

  return (
    <SafeAreaLayout
      edges={['top', 'left', 'right']}
      style={styles.screen}
    >
      <AppHeader showBackButton title="내 자산" />

      <RefreshableBouncyScrollView
        contentContainerStyle={styles.scrollContent}
        onRefresh={() => void refetch()}
        refreshing={isRefreshing}>
        <AssetOverviewCard overview={overview} />

        {analysis ? <AssetAnalysisCard analysis={analysis} /> : null}

        <View style={styles.sections}>
          {sections.map((section) => (
            <AssetSection
              action={section.id === 'checking' && transferAccounts.length >= 2
                ? {
                    label: '계좌 이체',
                    onPress: () => {
                      setTransferError(null);
                      setIsTransferOpen(true);
                    },
                  }
                : undefined}
              key={section.id}
              section={section}
            />
          ))}
        </View>
      </RefreshableBouncyScrollView>

      {isTransferOpen ? (
        <AccountTransferBottomSheet
          accounts={transferAccounts}
          errorMessage={transferError}
          isSubmitting={isTransferring}
          safebox={safeboxAccount
            ? { accountId: safeboxAccount.accountId, availableAmount: safeboxAccount.availableAmount }
            : null}
          onClose={() => {
            if (!isTransferring) setIsTransferOpen(false);
          }}
          onSubmit={confirmTransfer}
        />
      ) : null}
    </SafeAreaLayout>
  );
}
