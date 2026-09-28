package com.ssafy.dodam.domain.recommendation.repository;

import com.ssafy.dodam.domain.recommendation.entity.RecommendationReason;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 추천 근거 조회.
 *
 * <p>`recommendation_reason`에는 `user_id`가 없다. 상위 `recommendation`을 조인해 `userId` 조건을
 * <b>쿼리 안에서</b> 건다. `recommendationId`만 믿고 조회하면 타인의 근거가 그대로 노출된다.
 * 정렬은 `reasonId ASC`로, 생성기가 넘긴 순서를 유지한다.
 */
public interface RecommendationReasonRepository extends JpaRepository<RecommendationReason, Long> {

    @Query("""
            select rs
              from RecommendationReason rs, Recommendation r
             where rs.recommendationId = r.recommendationId
               and r.recommendationId = :recommendationId
               and r.userId = :userId
             order by rs.reasonId asc
            """)
    List<RecommendationReason> findOwned(
            @Param("userId") Long userId,
            @Param("recommendationId") Long recommendationId
    );

    /** 목록·priorities에서 추천 여러 건의 시뮬레이션 프리셋을 한 번에 조립할 때(587). 소유권 조건은 {@link #findOwned}와 같다. */
    @Query("""
            select rs
              from RecommendationReason rs, Recommendation r
             where rs.recommendationId = r.recommendationId
               and r.recommendationId in :recommendationIds
               and r.userId = :userId
             order by rs.recommendationId asc, rs.reasonId asc
            """)
    List<RecommendationReason> findAllOwnedIn(
            @Param("userId") Long userId,
            @Param("recommendationIds") Collection<Long> recommendationIds
    );
}
