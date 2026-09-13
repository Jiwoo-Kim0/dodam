package com.ssafy.dodam.domain.diagnosis.repository;

import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 진단 헤더 조회.
 *
 * <p><b>모든 조회에 `userId` 조건이 들어 있다.</b> `diagnosisId`만 믿고 조회하면 타인의 진단이 그대로
 * 노출된다. 타인 진단과 없는 진단은 모두 `Optional.empty()`이며 존재 여부를 구분해 노출하지 않는다.
 * `RESOURCE_NOT_FOUND`로 변환하는 것은 API 계층(544~546)의 몫이다.
 *
 * <p>완료 여부는 `completedAt`의 NULL 여부로 판별한다. ERD에 진단 실행 상태 컬럼이 없다.
 */
public interface DiagnosisRepository extends JpaRepository<Diagnosis, Long> {
    Page<Diagnosis> findByUserIdAndCompletedAtIsNotNullOrderByCreatedAtDescDiagnosisIdDesc(Long userId, Pageable pageable);

    Optional<Diagnosis> findFirstByUserIdAndGoalIdAndRuleVersionAndCompletedAtIsNotNullOrderByCompletedAtDescDiagnosisIdDesc(
            Long userId, Long goalId, String ruleVersion);

    boolean existsByUserIdAndDiagnosisIdGreaterThan(Long userId, Long diagnosisId);

    Optional<Diagnosis> findByDiagnosisIdAndUserId(Long diagnosisId, Long userId);

    /** 진단 이력. 명세 14의 목록 조회는 최신순이다. */
    Page<Diagnosis> findByUserIdOrderByCreatedAtDescDiagnosisIdDesc(Long userId, Pageable pageable);

    /**
     * 최신 완료 진단. 완료 시각이 같으면 나중에 저장된 쪽을 고른다.
     *
     * <p>완료되지 않은 진단은 하위행이 없거나 일부만 있는 상태라 확정값처럼 보여줄 수 없어 제외한다
     * (541이 cashflow에서 택한 기준과 같다).
     */
    Optional<Diagnosis> findFirstByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDescDiagnosisIdDesc(
            Long userId
    );

    /** 목표 연계 진단의 최신 완료본. 명세 `latest`의 `goalId` 필터다. */
    Optional<Diagnosis>
            findFirstByUserIdAndGoalIdAndCompletedAtIsNotNullOrderByCompletedAtDescDiagnosisIdDesc(
            Long userId, Long goalId
    );

    /**
     * 진행 중인 진단. 명세의 "중복 요청 방지"에 쓴다.
     *
     * <p>실패 상태 컬럼이 없어 계산 도중 프로세스가 내려간 진단은 `completedAt`이 영원히 NULL로 남는다.
     * 그래서 접수 시각으로 하한을 두고 그보다 오래된 행은 버려진 것으로 본다. 하한값은 호출자가 정한다.
     */
    Optional<Diagnosis> findFirstByUserIdAndCompletedAtIsNullAndCreatedAtAfterOrderByCreatedAtDesc(
            Long userId, LocalDateTime oldestAlive
    );
}
