package com.ssafy.dodam.domain.subscription.dto.response;

import com.ssafy.dodam.domain.card.entity.Card;
import com.ssafy.dodam.domain.subscription.entity.Subscription;
import com.ssafy.dodam.domain.subscription.entity.SubscriptionCategory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 정기결제 한 건. 목록·상세·수정·정지/재개 응답이 모두 같은 형태다.
 *
 * @param categoryLabel           카테고리 표시 이름. 정의되지 않은 코드는 코드 그대로
 * @param monthlyEquivalentAmount 월 환산 금액(WEEKLY ×52/12, YEARLY ÷12)
 * @param daysUntilNextPayment    오늘부터 다음 결제일까지 남은 일수. 다음 결제일이 없으면 null
 * @param cardName, cardNoMasked  결제 카드. 카드가 없거나 삭제됐으면 null
 */
public record SubscriptionResponse(
        String subscriptionId,
        String name,
        String category,
        String categoryLabel,
        long paymentAmount,
        String billingCycle,
        long monthlyEquivalentAmount,
        Integer paymentDay,
        LocalDate nextPaymentDate,
        Long daysUntilNextPayment,
        LocalDate endDate,
        String status,
        Long cardId,
        String cardName,
        String cardNoMasked,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime canceledAt
) {

    public static SubscriptionResponse from(Subscription subscription, Card card, LocalDate today) {
        LocalDate next = subscription.getNextPaymentDate();
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getName(),
                subscription.getCategory(),
                SubscriptionCategory.labelOf(subscription.getCategory()),
                subscription.getPaymentAmount(),
                subscription.getBillingCycle().name(),
                subscription.getMonthlyEquivalentAmount(),
                subscription.getPaymentDay(),
                next,
                next == null ? null : ChronoUnit.DAYS.between(today, next),
                subscription.getEndDate(),
                subscription.getStatus().name(),
                subscription.getCardId(),
                card == null ? null : card.getCardName(),
                card == null ? null : card.getCardNoMasked(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt(),
                subscription.getCanceledAt()
        );
    }
}
