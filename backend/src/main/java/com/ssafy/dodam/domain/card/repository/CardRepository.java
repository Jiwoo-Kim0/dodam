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

    /** 소유권 조건이 조회 자체에 들어간다. 시뮬레이션(586)이 cardId 입력의 본인 여부를 확인할 때 쓴다. */
    boolean existsByIdAndUserId(Long id, Long userId);
}
