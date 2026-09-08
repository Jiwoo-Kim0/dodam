package com.ssafy.dodam.domain.card.repository;

import com.ssafy.dodam.domain.card.entity.Card;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CardRepository extends
        JpaRepository<Card, Long>,
        JpaSpecificationExecutor<Card> {

    @Override
    @EntityGraph(attributePaths = "withdrawalAccount")
    List<Card> findAll(Specification<Card> specification, Sort sort);

    @EntityGraph(attributePaths = "withdrawalAccount")
    Optional<Card> findByUserIdAndCardNoHash(Long userId, String cardNoHash);
}
