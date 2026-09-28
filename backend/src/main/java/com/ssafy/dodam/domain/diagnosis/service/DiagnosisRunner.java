package com.ssafy.dodam.domain.diagnosis.service;

import com.ssafy.dodam.domain.diagnosis.dto.DiagnosisResponse;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisIssueRepository;
import com.ssafy.dodam.domain.pattern.dto.request.AnalyzePatternRequest;
import com.ssafy.dodam.domain.pattern.service.PatternAnalysisRunner;
import com.ssafy.dodam.global.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 진단 실행. 접수(잠금·트랜잭션) → 패턴 분석 보장(641) → 계산(트랜잭션 밖) → 완료(잠금·트랜잭션).
 *
 * <p>(641) FE가 {@code POST /patterns/analyze}를 호출하지 않으므로 군집 이탈 규칙이 읽을 분석을 진단이 먼저 보장한다.
 * 오늘 완료본이 있으면 재사용되고(같은 날 캐시), 데이터 부족·미연결(409)이나 그 밖의 실패는 진단을 막지 않는다 — 규칙이 coverage에
 * {@code PATTERN_NOT_ANALYZED} 등 사유를 남긴다. {@code admit} 트랜잭션이 커밋된 뒤 {@code evaluate} 앞에서 호출하므로 잠금이 겹치지 않는다.
 */
@Service
public class DiagnosisRunner {
    private static final Logger log = LoggerFactory.getLogger(DiagnosisRunner.class);

    private final DiagnosisExecutionStore store;
    private final DiagnosisEvaluator evaluator;
    private final PatternAnalysisRunner patterns;
    private final DiagnosisIssueRepository issues;

    public DiagnosisRunner(DiagnosisExecutionStore store, DiagnosisEvaluator evaluator, PatternAnalysisRunner patterns,
            DiagnosisIssueRepository issues) {
        this.store = store;
        this.evaluator = evaluator;
        this.patterns = patterns;
        this.issues = issues;
    }

    public Result run(Long userId, Long goalId, boolean forceRefresh) {
        var admission = store.admit(userId, goalId, forceRefresh);
        if (admission.cached()) {
            return new Result(toResponse(admission.diagnosis()), true);
        }
        Long id = admission.diagnosis().getDiagnosisId();
        ensurePatternAnalysis(userId);
        try {
            var result = evaluator.evaluate(admission.diagnosis());
            return new Result(toResponse(store.complete(userId, id, result)), false);
        } catch (RuntimeException failure) {
            try {
                store.abandon(userId, id);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /** 패턴 분석 실패는 진단 실패가 아니다. 사유는 군집 이탈 규칙이 coverage로 남기므로 여기서는 로그만 남긴다. */
    private void ensurePatternAnalysis(Long userId) {
        try {
            patterns.run(userId, AnalyzePatternRequest.defaults());
        } catch (BusinessException precondition) {
            log.debug("Pattern analysis skipped before diagnosis (userId={}): {}", userId, precondition.getErrorCode());
        } catch (RuntimeException failure) {
            log.warn("Pattern analysis failed before diagnosis (userId={}): {}", userId, failure.getMessage());
        }
    }

    /** 675: 발견 화면용 문제 수(GOAL 제외)를 함께 싣는다. 캐시 응답도 저장된 문제로 같은 값을 만든다. */
    private DiagnosisResponse toResponse(Diagnosis diagnosis) {
        return DiagnosisResponse.from(diagnosis,
                issues.countByDiagnosisIdAndDomain(diagnosis.getDiagnosisId(), DiagnosisResponse.DISCOVERY_EXCLUDED_DOMAIN));
    }

    public record Result(DiagnosisResponse diagnosis, boolean cached) { }
}
