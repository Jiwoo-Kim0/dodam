package com.ssafy.dodam.domain.improvement.repository;

import com.ssafy.dodam.domain.improvement.entity.ImprovementAction;
import com.ssafy.dodam.domain.improvement.entity.ImprovementActionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 개선 행동 조회. 모든 조회에 `userId` 조건을 건다 — 타인 행동과 없는 행동은 모두 빈 결과이며 존재 여부를 구분해
 * 노출하지 않는다(API 계층에서 RESOURCE_NOT_FOUND로 변환).
 *
 * <p>정렬은 `plannedAt DESC, actionId DESC`로 고정한다. 같은 생성 요청에서 여러 추천을 연달아 수락하면
 * `plannedAt`이 같으므로 id로 tie-break해야 페이지 경계가 안정된다(추천 목록과 같은 규칙).
 */
public interface ImprovementActionRepository extends JpaRepository<ImprovementAction, Long> {

    Optional<ImprovementAction> findByActionIdAndUserId(Long actionId, Long userId);

    /** 추천 1건당 행동은 최대 1건이다. 재수락이 두 번째 행을 만들지 못하게 하는 검사에 쓴다. */
    Optional<ImprovementAction> findByRecommendationId(Long recommendationId);

    List<ImprovementAction> findAllByUserId(Long userId);

    /**
     * API 19 목록. `status`가 null이면 조건에서 빠진다. 기간은 수락 시각(`plannedAt`) 기준이다 —
     * 완료·측정 시각으로 거르면 아직 실행 중인 행동이 기간 조회에서 사라진다.
     */
    @Query("""
            select a
              from ImprovementAction a
             where a.userId = :userId
               and (:status is null or a.status = :status)
               and a.plannedAt >= :from
               and a.plannedAt < :toExclusive
             order by a.plannedAt desc, a.actionId desc
            """)
    Page<ImprovementAction> findOwned(
            @Param("userId") Long userId,
            @Param("status") ImprovementActionStatus status,
            @Param("from") LocalDateTime from,
            @Param("toExclusive") LocalDateTime toExclusive,
            Pageable pageable
    );
}
