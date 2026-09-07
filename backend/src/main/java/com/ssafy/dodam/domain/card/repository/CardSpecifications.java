package com.ssafy.dodam.domain.card.repository;

import com.ssafy.dodam.domain.card.entity.Card;
import com.ssafy.dodam.domain.card.entity.CardStatus;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class CardSpecifications {

    private CardSpecifications() {
    }

    public static Specification<Card> ownedBy(Long userId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(
                root.get("userId"),
                userId
        );
    }

    public static Specification<Card> hasIssuerCode(String issuerCode) {
        if (issuerCode == null) {
            return unrestricted();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(
                criteriaBuilder.upper(root.get("issuerCode")),
                issuerCode.toUpperCase(Locale.ROOT)
        );
    }

    public static Specification<Card> hasStatus(CardStatus status) {
        if (status == null) {
            return unrestricted();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(
                root.get("status"),
                status
        );
    }

    private static Specification<Card> unrestricted() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
    }
}
