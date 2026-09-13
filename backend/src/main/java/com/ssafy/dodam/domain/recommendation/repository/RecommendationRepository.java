package com.ssafy.dodam.domain.recommendation.repository;

import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 추천 헤더 조회.
 *
 * <p>모든 조회에 `userId` 조건을 건다. 타인 추천과 없는 추천은 모두 `Optional.empty()`·빈 목록이며
 * 존재 여부를 구분해 노출하지 않는다(API 계층에서 RESOURCE_NOT_FOUND로 변환).
 *
 * <p>정렬은 `createdAt DESC, recommendationId DESC`로 고정한다. 같은 생성 요청의 추천은 `createdAt`이
 * 같으므로 id로 tie-break해야 페이지 경계가 안정된다. 우선순위(priority) 정렬은 API 16 priorities가
 * 요구하지만 점수 계산이 548에 있으므로 여기서 정렬 계약을 미리 정하지 않는다.
 */
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    Optional<Recommendation> findByRecommendationIdAndUserId(Long recommendationId, Long userId);

    /** 중복 생성 검사용. 같은 사용자 안에서 `generationKey`는 유일하다. */
    Optional<Recommendation> findByUserIdAndGenerationKey(Long userId, String generationKey);

    /** API 16 목록. `status`·`domain`은 선택 필터이며 NULL이면 조건에서 빠진다. */
    @Query("""
            select r
              from Recommendation r
             where r.userId = :userId
               and (:status is null or r.status = :status)
               and (:domain is null or r.domain = :domain)
             order by r.createdAt desc, r.recommendationId desc
            """)
    Page<Recommendation> findOwned(
            @Param("userId") Long userId,
            @Param("status") RecommendationStatus status,
            @Param("domain") String domain,
            Pageable pageable
    );

    /** 한 진단에서 만들어진 추천 전체. 재생성 응답과 목표기여 집계처럼 페이지를 나누지 않는 경로용. */
    List<Recommendation> findAllByUserIdAndDiagnosisIdOrderByCreatedAtDescRecommendationIdDesc(
            Long userId, Long diagnosisId);
}
