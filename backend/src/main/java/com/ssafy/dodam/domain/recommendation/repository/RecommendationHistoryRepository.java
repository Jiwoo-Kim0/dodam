package com.ssafy.dodam.domain.recommendation.repository;

import com.ssafy.dodam.domain.recommendation.entity.RecommendationHistory;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 추천 이력 조회.
 *
 * <p>`recommendation_history`에도 `user_id`가 없어 상위 `recommendation`을 조인해 소유권을 건다.
 * 정렬은 최신순 `changedAt DESC, recommendationHistoryId DESC`다. 같은 트랜잭션에서 만들어진 이력은
 * `changedAt`이 같을 수 있어 id로 tie-break한다.
 */
public interface RecommendationHistoryRepository extends JpaRepository<RecommendationHistory, Long> {

    /** 추천 1건의 이력 전체. */
    @Query("""
            select h
              from RecommendationHistory h, Recommendation r
             where h.recommendationId = r.recommendationId
               and r.recommendationId = :recommendationId
               and r.userId = :userId
             order by h.changedAt desc, h.recommendationHistoryId desc
            """)
    List<RecommendationHistory> findOwned(
            @Param("userId") Long userId,
            @Param("recommendationId") Long recommendationId
    );

    /**
     * API 16 `GET /recommendations/history`용. 사용자의 모든 추천에 걸친 이력을 기간으로 자른다.
     *
     * <p>기간은 `[from, to)` 반개구간이다. 일 단위 `startDate~endDate`는 호출자가 `endDate + 1일 00:00`으로
     * 바꿔 넘긴다. 기본 기간·최대 기간 검증은 API 계층(`QueryPeriodPolicy`)에서 한다.
     */
    @Query("""
            select h
              from RecommendationHistory h, Recommendation r
             where h.recommendationId = r.recommendationId
               and r.userId = :userId
               and h.changedAt >= :from
               and h.changedAt < :to
             order by h.changedAt desc, h.recommendationHistoryId desc
            """)
    Page<RecommendationHistory> findOwnedInPeriod(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
}
