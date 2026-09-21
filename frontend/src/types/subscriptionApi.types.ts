/** 정기결제 결제 주기. 서버 `Subscription.billingCycle`과 같은 값이다. */
export type SubscriptionBillingCycle = 'WEEKLY' | 'MONTHLY' | 'YEARLY';

/** 정기결제 상태. 종료 상태인 CANCELED·EXPIRED는 사용자가 되돌릴 수 없다. */
export type SubscriptionStatus = 'ACTIVE' | 'PAUSED' | 'EXPIRED' | 'CANCELED';

/**
 * 정기결제 한 건. 목록·상세·수정·정지/재개 응답이 모두 같은 형태다.
 * 원천 규약은 backend/docs/subscription.md를 따른다.
 */
export interface SubscriptionApiResponse {
  subscriptionId: string;
  name: string;
  category: string | null;
  /** 카테고리 표시 이름. 서버에 정의되지 않은 코드는 코드 그대로 내려온다. */
  categoryLabel: string | null;
  paymentAmount: number;
  billingCycle: SubscriptionBillingCycle;
  /** 월 환산 금액. WEEKLY는 ×52÷12, YEARLY는 ÷12를 원 단위로 반올림한 값이다. */
  monthlyEquivalentAmount: number;
  paymentDay: number | null;
  nextPaymentDate: string | null;
  daysUntilNextPayment: number | null;
  endDate: string | null;
  status: SubscriptionStatus;
  cardId: number | null;
  cardName: string | null;
  cardNoMasked: string | null;
  createdAt: string;
  updatedAt: string | null;
  canceledAt: string | null;
}
