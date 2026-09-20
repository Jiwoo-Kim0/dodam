import { useCallback, useRef, useState } from 'react';

import { fetchAllPages } from '@/api/pagination';
import { subscriptionApi } from '@/api';
import type { SubscriptionApiResponse } from '@/types/subscriptionApi.types';

/** 화면이 쓰는 정기결제 한 건. */
export interface SubscriptionListItemModel {
  id: string;
  name: string;
  /** 목록에 찍고 합계에 더하는 금액. 주기가 달라도 비교할 수 있도록 월 환산 금액을 쓴다. */
  monthlyAmount: number;
  /** 월 결제가 아닐 때만 채운다. 월 환산 금액이 실제 청구액과 다르다는 것을 알려 준다. */
  billingCycleNote: string | null;
}

const BILLING_CYCLE_NOTES: Readonly<Record<string, string>> = {
  WEEKLY: '주 결제를 월로 환산했어요',
  YEARLY: '연 결제를 월로 환산했어요',
};

function toListItem(subscription: SubscriptionApiResponse): SubscriptionListItemModel {
  return {
    id: subscription.subscriptionId,
    name: subscription.name,
    monthlyAmount: subscription.monthlyEquivalentAmount,
    billingCycleNote: BILLING_CYCLE_NOTES[subscription.billingCycle] ?? null,
  };
}

/**
 * 지금 이용 중인 정기결제 목록.
 *
 * 정지·만료·해지한 건은 "이용 중"이 아니므로 ACTIVE만 조회한다.
 * 거래 원장에서 반복결제를 찾는 `/subscriptions/candidates`는 쓰지 않는다. 같은 금액이
 * 일정 간격으로 나가는 것만 보는 탐지라 보험료·주거비 같은 고정비까지 구독으로 잡힌다.
 */
export function useSubscriptions() {
  const [subscriptions, setSubscriptions] = useState<SubscriptionListItemModel[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const requestIdRef = useRef(0);

  // 당겨서 새로고침일 때는 전체 로딩 화면 대신 스크롤 상단 인디케이터만 돈다.
  const load = useCallback(async (refreshing = false) => {
    const requestId = ++requestIdRef.current;
    if (refreshing) {
      setIsRefreshing(true);
    } else {
      setIsLoading(true);
    }
    setError(null);

    try {
      const items = await fetchAllPages((page, size) =>
        subscriptionApi.getSubscriptions({ status: 'ACTIVE', page, size }),
      );
      if (requestId !== requestIdRef.current) return;

      setSubscriptions(items.map(toListItem));
    } catch (loadError) {
      if (requestId !== requestIdRef.current) return;

      setSubscriptions([]);
      setError(
        loadError instanceof Error
          ? loadError
          : new Error('구독 목록을 불러오지 못했어요.'),
      );
    } finally {
      if (requestId === requestIdRef.current) {
        setIsLoading(false);
        setIsRefreshing(false);
      }
    }
  }, []);

  return {
    subscriptions,
    error,
    isLoading,
    isRefreshing,
    refetch: useCallback(() => load(true), [load]),
    reload: load,
    /** 화면을 벗어날 때 부른다. 늦게 도착한 응답이 다음 화면 상태를 덮어쓰지 않게 한다. */
    cancel: useCallback(() => {
      requestIdRef.current += 1;
    }, []),
  };
}
