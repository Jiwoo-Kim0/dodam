package com.ssafy.dodam.domain.diagnosis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisStatus;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisIssueRepository;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 진단 실행의 저장 경로.
 *
 * <p>진단 규칙과 HTTP 계약은 여기 없다. 규칙 실행은 S15P21D105-544, 문제 응답 변환은 545가 맡는다.
 * 이 클래스가 책임지는 것은 <b>접수 → 하위행 저장 → 완료</b>의 트랜잭션 경계다.
 *
 * <p>접수({@link #start})와 완료({@link #complete})를 하나의 트랜잭션으로 묶지 않는다. 규칙 실행이
 * 여러 도메인을 조회하며 수 초가 걸릴 수 있어, 한 트랜잭션으로 묶으면 계산하는 동안 커넥션을
 * 붙잡는다. 대신 헤더를 먼저 남겨 진행 중인 진단을 다른 요청이 알아볼 수 있게 한다(538·540이
 * cashflow에서 택한 구조와 같다).
 */
@Service
public class DiagnosisStore {

    private final DiagnosisRepository diagnosisRepository;
    private final DiagnosisIssueRepository issueRepository;

    public DiagnosisStore(
            DiagnosisRepository diagnosisRepository,
            DiagnosisIssueRepository issueRepository
    ) {
        this.diagnosisRepository = diagnosisRepository;
        this.issueRepository = issueRepository;
    }

    /**
     * 진단을 접수한다. 이 시점의 행은 `completed_at`이 NULL이라 조회 API가 대상으로 삼지 않는다.
     *
     * @param goalId 목표 연계 진단이 아니면 null
     */
    @Transactional
    public Diagnosis start(Long userId, Long goalId, LocalDateTime now) {
        return diagnosisRepository.save(Diagnosis.start(userId, goalId, now));
    }

    /**
     * 탐지 결과를 저장하고 진단을 완료 처리한다.
     *
     * <p>하위행을 먼저 지우고 다시 넣는다. 같은 진단을 재계산할 때 이전 결과가 남으면 문제가 중복
     * 집계된다. 삭제·삽입·완료 처리는 한 트랜잭션이어야 부분 반영된 진단이 조회되지 않는다.
     *
     * <p><b>JDBC 배치를 쓰지 않고 `saveAll`을 쓴다.</b> 538이 cashflow 하위행에 JDBC 배치를 쓴 것은
     * 예측 1건이 horizon 일수(90행 이상)를 만들기 때문이다. 진단 문제는 규칙 수만큼이라 규모가 다르고,
     * JSON 컬럼이 4개다. JDBC로 직접 넣으면 Hibernate의 JSON 변환을 우회해 `{"a":1}`이 객체가 아니라
     * JSON 문자열로 저장된다(H2에서 재현 확인). 방언별 JSON 처리를 직접 떠안는 대신 Hibernate에 맡긴다.
     *
     * <p>집계값은 호출자가 넘기지 않고 <b>저장하는 문제 목록에서 직접 센다.</b> 두 값을 따로 받으면
     * 실제 저장된 행과 어긋난 집계가 남을 수 있고, 그 어긋남은 `chk_diagnosis_high_severity_count`가
     * 잡아주지도 못한다(상하한 관계만 검사한다).
     *
     * @param overallStatus 종합 상태. 판정 규칙은 544가 정하므로 여기서 계산하지 않는다
     * <p>모든 문제가 이 진단에 속하는지 먼저 확인한다. 다른 `diagnosisId`를 가진 문제가 섞이면 헤더
     * 집계는 넘겨받은 목록으로 계산되는데 행은 다른 진단 밑에 들어가, 집계와 실제 행이 조용히
     * 어긋난다. `chk_diagnosis_high_severity_count`는 상하한 관계만 보므로 이 어긋남을 잡지 못한다.
     *
     * @param inputSnapshot 진단이 사용한 입력값 JSON. 형식은 544가 정한다. 없으면 null
     */
    @Transactional
    public void complete(
            Diagnosis diagnosis,
            List<DiagnosisIssue> issues,
            DiagnosisStatus overallStatus,
            JsonNode inputSnapshot,
            LocalDateTime now
    ) {
        Long diagnosisId = diagnosis.getDiagnosisId();
        requireIssuesBelongTo(diagnosisId, issues);
        issueRepository.deleteByDiagnosisId(diagnosisId);
        issueRepository.saveAll(issues);

        int highSeverityCount = (int) issues.stream().filter(DiagnosisIssue::isHighSeverity).count();
        diagnosis.complete(
                issues.size(),
                highSeverityCount,
                overallStatus,
                DiagnosisRuleVersion.CURRENT,
                inputSnapshot,
                now);
        diagnosisRepository.save(diagnosis);
    }

    /**
     * 계산에 실패한 진단의 하위행을 지운다.
     *
     * <p><b>실패를 표시할 컬럼이 없다.</b> ERD의 `diagnosis`에는 상태 컬럼이 없고 `completed_at`만
     * 있어, 실패한 진단은 접수된 채 `completed_at`이 NULL로 남는다. 진행 중인 진단과 구분되지 않으므로
     * 호출자는 접수 시각으로 만료를 판단해야 한다
     * ({@code DiagnosisRepository#findFirstByUserIdAndCompletedAtIsNullAndCreatedAtAfter...}).
     *
     * <p>그래도 하위행은 지운다. 남겨두면 다음 재계산에서 이전 문제가 중복 집계된다.
     */
    @Transactional
    public void discardIssues(Diagnosis diagnosis) {
        issueRepository.deleteByDiagnosisId(diagnosis.getDiagnosisId());
    }

    private void requireIssuesBelongTo(Long diagnosisId, List<DiagnosisIssue> issues) {
        boolean mismatched = issues.stream()
                .anyMatch(issue -> !diagnosisId.equals(issue.getDiagnosisId()));
        if (mismatched) {
            throw new IllegalArgumentException("다른 진단의 문제가 섞여 있습니다: " + diagnosisId);
        }
    }
}
