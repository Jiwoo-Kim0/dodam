package com.ssafy.dodam.domain.subscription.service;

import com.ssafy.dodam.domain.card.entity.Card;
import com.ssafy.dodam.domain.card.repository.CardRepository;
import com.ssafy.dodam.domain.subscription.dto.response.SubscriptionResponse;
import com.ssafy.dodam.domain.subscription.entity.Subscription;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 정기결제 응답에 결제 카드 이름·마스킹 번호를 붙인다. 카드는 한 번에 읽어 N+1을 피한다.
 */
@Component
public class SubscriptionResponseAssembler {

    private final CardRepository cardRepository;

    public SubscriptionResponseAssembler(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    public SubscriptionResponse toResponse(Subscription subscription, LocalDate today) {
        return toResponses(List.of(subscription), today).get(0);
    }

    public List<SubscriptionResponse> toResponses(List<Subscription> subscriptions, LocalDate today) {
        List<Long> cardIds = subscriptions.stream()
                .map(Subscription::getCardId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Card> cards = cardIds.isEmpty()
                ? Map.of()
                : cardRepository.findAllById(cardIds).stream()
                        .collect(Collectors.toMap(Card::getId, Function.identity()));
        return subscriptions.stream()
                .map(subscription -> SubscriptionResponse.from(
                        subscription, subscription.getCardId() == null ? null : cards.get(subscription.getCardId()), today))
                .toList();
    }
}
