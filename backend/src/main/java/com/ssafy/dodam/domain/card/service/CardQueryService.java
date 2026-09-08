package com.ssafy.dodam.domain.card.service;

import static com.ssafy.dodam.domain.card.repository.CardSpecifications.hasIssuerCode;
import static com.ssafy.dodam.domain.card.repository.CardSpecifications.hasStatus;
import static com.ssafy.dodam.domain.card.repository.CardSpecifications.ownedBy;

import com.ssafy.dodam.domain.card.dto.response.CardDetailResponse;
import com.ssafy.dodam.domain.card.dto.response.CardIssuerSummaryResponse;
import com.ssafy.dodam.domain.card.dto.response.CardResponse;
import com.ssafy.dodam.domain.card.dto.response.CardSummaryResponse;
import com.ssafy.dodam.domain.card.dto.response.CardTransactionResponse;
import com.ssafy.dodam.domain.card.entity.Card;
import com.ssafy.dodam.domain.card.entity.CardStatus;
import com.ssafy.dodam.domain.card.repository.CardRepository;
import com.ssafy.dodam.domain.card.repository.CardTransactionReader;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.policy.QueryPeriod;
import com.ssafy.dodam.global.policy.QueryPeriodPolicy;
import com.ssafy.dodam.global.response.PageResponse;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class CardQueryService {

    private static final Sort CARD_SORT = Sort.by(
            Sort.Order.asc("status"),
            Sort.Order.asc("cardName"),
            Sort.Order.asc("id")
    );

    private final CardRepository cardRepository;
    private final CardTransactionReader cardTransactionReader;
    private final SensitiveNumberHasher sensitiveNumberHasher;
    private final QueryPeriodPolicy queryPeriodPolicy;

    public CardQueryService(
            CardRepository cardRepository,
            CardTransactionReader cardTransactionReader,
            SensitiveNumberHasher sensitiveNumberHasher,
            QueryPeriodPolicy queryPeriodPolicy
    ) {
        this.cardRepository = cardRepository;
        this.cardTransactionReader = cardTransactionReader;
        this.sensitiveNumberHasher = sensitiveNumberHasher;
        this.queryPeriodPolicy = queryPeriodPolicy;
    }

    public List<CardResponse> getCards(Long userId, String issuerCode, String status) {
        return findCards(userId, issuerCode, status).stream()
                .map(CardResponse::from)
                .toList();
    }

    public CardSummaryResponse getSummary(Long userId) {
        List<Card> cards = findCards(userId, null, null);

        long activeCardCount = 0;
        long totalCreditLimit = 0;
        long totalAnnualFee = 0;
        Map<String, IssuerAccumulator> issuers = new LinkedHashMap<>();

        for (Card card : cards) {
            boolean active = card.getStatus() == CardStatus.ACTIVE;
            if (active) {
                activeCardCount++;
            }
            totalCreditLimit += card.getCreditLimit();
            totalAnnualFee += card.getAnnualFee() == null ? 0 : card.getAnnualFee();

            issuers.computeIfAbsent(
                    card.getIssuerCode(),
                    issuerCode -> new IssuerAccumulator(issuerCode, card.getIssuerName())
            ).add(card.getCreditLimit(), active);
        }

        List<CardIssuerSummaryResponse> issuerSummaries = new ArrayList<>(issuers.size());
        issuers.values().forEach(accumulator -> issuerSummaries.add(accumulator.toResponse()));

        return new CardSummaryResponse(
                cards.size(),
                activeCardCount,
                totalCreditLimit,
                totalAnnualFee,
                issuerSummaries
        );
    }

    public CardDetailResponse getCard(Long userId, String cardNo) {
        return CardDetailResponse.from(findCard(userId, cardNo));
    }

    public PageResponse<CardTransactionResponse> getCardTransactions(
            Long userId,
            String cardNo,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    ) {
        Card card = findCard(userId, cardNo);
        QueryPeriod period = queryPeriodPolicy.resolve(startDate, endDate);
        Pageable pageable = PageRequest.of(page, size);

        return PageResponse.from(
                cardTransactionReader.read(userId, card.getId(), period, pageable),
                CardTransactionResponse::from
        );
    }

    private List<Card> findCards(Long userId, String issuerCode, String status) {
        return cardRepository.findAll(
                ownedBy(userId)
                        .and(hasIssuerCode(normalize(issuerCode)))
                        .and(hasStatus(parseStatus(status))),
                CARD_SORT
        );
    }

    private Card findCard(Long userId, String cardNo) {
        return cardRepository
                .findByUserIdAndCardNoHash(userId, sensitiveNumberHasher.hash(cardNo))
                .orElseThrow(() -> new ResourceNotFoundException("카드를 찾을 수 없습니다."));
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static CardStatus parseStatus(String status) {
        String normalized = normalize(status);
        if (normalized == null) {
            return null;
        }
        try {
            return CardStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("지원하지 않는 카드 상태입니다.");
        }
    }

    private static final class IssuerAccumulator {

        private final String issuerCode;
        private final String issuerName;
        private long cardCount;
        private long activeCardCount;
        private long totalCreditLimit;

        private IssuerAccumulator(String issuerCode, String issuerName) {
            this.issuerCode = issuerCode;
            this.issuerName = issuerName;
        }

        private void add(long creditLimit, boolean active) {
            cardCount++;
            if (active) {
                activeCardCount++;
            }
            totalCreditLimit += creditLimit;
        }

        private CardIssuerSummaryResponse toResponse() {
            return new CardIssuerSummaryResponse(
                    issuerCode,
                    issuerName,
                    cardCount,
                    activeCardCount,
                    totalCreditLimit
            );
        }
    }
}
