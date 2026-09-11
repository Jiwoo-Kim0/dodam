package com.ssafy.dodam.domain.diagnosis.repository;

import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 탐지 문제 조회.
 *
 * <p>`diagnosis_issue`에는 `user_id`가 없다. 모든 조회는 상위 `diagnosis`를 조인해 `userId` 조건을
 * <b>쿼리 안에서</b> 건다. `diagnosisId`만 믿고 조회하면 타인의 문제 목록이 그대로 노출된다.
 *
 * <p>`domain`·`type`·`severity`는 명세 14의 선택 필터이며 NULL이면 조건에서 빠진다. 정렬은 심각도
 * 우선이 자연스럽지만 `severity`가 문자열 컬럼이라 사전순이 심각도순과 다르다. 정렬 규칙은 응답을
 * 만드는 S15P21D105-545에서 정하고 여기서는 `issueId` 기준으로만 정렬한다.
 */
public interface DiagnosisIssueRepository extends JpaRepository<DiagnosisIssue, Long> {

    @Query("""
            select i
              from DiagnosisIssue i, Diagnosis d
             where i.diagnosisId = d.diagnosisId
               and d.diagnosisId = :diagnosisId
               and d.userId = :userId
               and (:domain is null or i.domain = :domain)
               and (:type is null or i.type = :type)
               and (:severity is null or i.severity = :severity)
             order by i.issueId asc
            """)
    Page<DiagnosisIssue> findOwned(
            @Param("userId") Long userId,
            @Param("diagnosisId") Long diagnosisId,
            @Param("domain") String domain,
            @Param("type") String type,
            @Param("severity") IssueSeverity severity,
            Pageable pageable
    );

    /** 문제 1건. 진단과 사용자 모두 일치해야 한다. */
    @Query("""
            select i
              from DiagnosisIssue i, Diagnosis d
             where i.diagnosisId = d.diagnosisId
               and i.issueId = :issueId
               and d.diagnosisId = :diagnosisId
               and d.userId = :userId
            """)
    Optional<DiagnosisIssue> findOwnedById(
            @Param("userId") Long userId,
            @Param("diagnosisId") Long diagnosisId,
            @Param("issueId") Long issueId
    );

    /** 요약·목표영향 계산처럼 전체를 한 번에 읽어야 하는 경로용. 페이지를 나누지 않는다. */
    @Query("""
            select i
              from DiagnosisIssue i, Diagnosis d
             where i.diagnosisId = d.diagnosisId
               and d.diagnosisId = :diagnosisId
               and d.userId = :userId
             order by i.issueId asc
            """)
    List<DiagnosisIssue> findAllOwned(
            @Param("userId") Long userId,
            @Param("diagnosisId") Long diagnosisId
    );

    /**
     * 재진단 시 이전 하위행을 지운다.
     *
     * <p>파생 삭제를 쓰면 Hibernate가 엔티티를 지연 삭제해 DELETE가 이어지는 INSERT보다 늦게 나갈 수
     * 있다. 그러면 이전 행이 남은 상태에서 새 문제가 삽입돼 중복 집계된다. 벌크 DML로 즉시 삭제한다
     * (538이 cashflow에서 겪은 문제와 같다).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DiagnosisIssue i where i.diagnosisId = :diagnosisId")
    void deleteByDiagnosisId(@Param("diagnosisId") Long diagnosisId);
}
