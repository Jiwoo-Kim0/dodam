package com.ssafy.dodam.domain.pattern.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.pattern.dto.response.PatternFeatureSummary.SourceFingerprint;
import com.ssafy.dodam.domain.pattern.entity.AnalysisMode;
import com.ssafy.dodam.domain.pattern.entity.PatternAnalysis;
import com.ssafy.dodam.domain.pattern.entity.PatternAnalysisStatus;
import com.ssafy.dodam.domain.pattern.repository.PatternAnalysisRepository;
import com.ssafy.dodam.domain.pattern.repository.PatternUserLock;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 접수와 완료에서만 사용자 행을 잠근다. 계산(집계 조회·군집 학습)은 이 트랜잭션 밖에서 실행한다. Diagnosis의
 * `DiagnosisExecutionStore`와 같은 구조다.
 *
 * <p><b>중복 요청 방지</b>: 임대 기간(`app.pattern.lease-duration`) 안의 RUNNING 행이 있으면 409. 임대가 지난 RUNNING 행은 죽은
 * 접수로 보고 새 요청을 받는다(그 행은 완료 시점에 만료 처리된다).
 *
 * <p><b>재사용</b>: `forceRefresh=false`이고 오늘(서비스 시간대) 완료된 결과가 같은 기간·같은 요청 모드로 있고, 그 결과에 저장된
 * 원천 거래 지문이 지금 지문과 같으면 새로 계산하지 않고 그 결과를 돌려준다. 요청 모드가 다르거나(RULE_ONLY ↔ HYBRID) 분석 기간
 * 안 거래가 추가·삭제·숨김·분류 변경으로 달라졌으면(700) 재사용하지 않는다. 지문이 없는 700 이전 결과도 재사용하지 않는다 —
 * 오래된 결과를 돌려줄 가능성보다 하루 한 번의 재계산 비용이 싸다.
 */
@Service
public class PatternAnalysisExecutionStore {

    private final PatternUserLock users;
    private final PatternAnalysisRepository analyses;
    private final PatternResponseAssembler assembler;
    private final PatternProperties properties;
    private final PatternClock clock;

    public PatternAnalysisExecutionStore(PatternUserLock users, PatternAnalysisRepository analyses,
            PatternResponseAssembler assembler, PatternProperties properties, PatternClock clock) {
        this.users = users;
        this.analyses = analyses;
        this.assembler = assembler;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Admission admit(Long userId, AnalysisMode requestedMode, boolean forceRefresh, int lookbackMonths,
            SourceFingerprint fingerprint) {
        lock(userId);
        LocalDateTime now = clock.now();
        if (analyses.findFirstByUserIdAndStatusAndAnalyzedAtAfterOrderByIdDesc(
                userId, PatternAnalysisStatus.RUNNING, now.minus(properties.leaseDuration())).isPresent()) {
            throw new BusinessException(ErrorCode.PATTERN_ANALYSIS_IN_PROGRESS);
        }
        if (!forceRefresh) {
            Optional<PatternAnalysis> reusable = analyses
                    .findFirstByUserIdAndStatusOrderByAnalyzedAtDescIdDesc(userId, PatternAnalysisStatus.COMPLETED)
                    .filter(latest -> latest.getAnalyzedAt().toLocalDate().equals(now.toLocalDate()))
                    .filter(latest -> latest.getLookbackMonths() == lookbackMonths)
                    .filter(latest -> assembler.featureSummary(latest)
                            .map(summary -> requestedMode.name().equals(summary.requestedMode())
                                    && fingerprint.equals(summary.sourceFingerprint()))
                            .orElse(false));
            if (reusable.isPresent()) {
                return new Admission(reusable.get(), true);
            }
        }
        return new Admission(analyses.save(PatternAnalysis.start(userId, lookbackMonths, now)), false);
    }

    @Transactional
    public PatternAnalysis complete(Long userId, Long analysisId, Completion completion) {
        lock(userId);
        LocalDateTime now = clock.now();
        PatternAnalysis analysis = running(userId, analysisId, now);
        PatternFeatures f = completion.features();
        analysis.complete(f.dataPointCount(), f.spendingRegularity(), f.savingConsistency(), f.fixedExpenseRatio(),
                f.variableExpenseRatio(), f.discretionarySpendingRatio(), f.debtBurden(), f.cashflowStability(),
                f.liquidityLevel(), completion.featureSummary(), now);
        if (completion.cluster() != null) {
            ClusterAttachment c = completion.cluster();
            analysis.attachCluster(c.modelVersion(), c.clusterId(), c.clusterLabel(), c.featureWindowMonths(), c.sampleSize(),
                    c.confidence(), c.clusterStats());
            if (c.peerStats() != null) {
                analysis.attachPeerStats(c.peerStats());
            }
        }
        return analysis;
    }

    /** 선행 조건 위반(데이터 부족 등)으로 끝난 접수는 행을 남기지 않는다. 완료 결과는 삭제하지 않는다. */
    @Transactional
    public void abandon(Long userId, Long analysisId) {
        lock(userId);
        analyses.findByIdAndUserId(analysisId, userId).filter(PatternAnalysis::isRunning).ifPresent(analyses::delete);
    }

    /** 예상하지 못한 계산 오류는 FAILED로 남겨 원인을 추적한다. 조회 API는 COMPLETED만 보므로 사용자에게 노출되지 않는다. */
    @Transactional
    public void fail(Long userId, Long analysisId, JsonNode failureSummary) {
        lock(userId);
        analyses.findByIdAndUserId(analysisId, userId).filter(PatternAnalysis::isRunning)
                .ifPresent(analysis -> analysis.fail(failureSummary, clock.now()));
    }

    private PatternAnalysis running(Long userId, Long analysisId, LocalDateTime now) {
        PatternAnalysis analysis = analyses.findByIdAndUserId(analysisId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PATTERN_ANALYSIS_EXPIRED));
        boolean leaseExpired = !analysis.getAnalyzedAt().plus(properties.leaseDuration()).isAfter(now);
        if (!analysis.isRunning() || leaseExpired || analyses.existsByUserIdAndIdGreaterThan(userId, analysisId)) {
            throw new BusinessException(ErrorCode.PATTERN_ANALYSIS_EXPIRED);
        }
        return analysis;
    }

    private void lock(Long userId) {
        users.lock(userId).orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
    }

    public record Admission(PatternAnalysis analysis, boolean cached) {
    }

    /** 완료에 필요한 계산 결과. `cluster`가 null이면 규칙 지표만 저장한다. */
    public record Completion(PatternFeatures features, JsonNode featureSummary, ClusterAttachment cluster) {
    }

    /**
     * @param peerStats (632) 차원별 동년배 비교 JSON. 모델에 분위 격자가 없어도 사유가 담긴 응답을 저장하므로 null이 아니다
     */
    public record ClusterAttachment(
            String modelVersion,
            int clusterId,
            String clusterLabel,
            int featureWindowMonths,
            int sampleSize,
            double confidence,
            JsonNode clusterStats,
            JsonNode peerStats
    ) {
        public ClusterAttachment(String modelVersion, int clusterId, String clusterLabel, int featureWindowMonths, int sampleSize,
                double confidence, JsonNode clusterStats) {
            this(modelVersion, clusterId, clusterLabel, featureWindowMonths, sampleSize, confidence, clusterStats, null);
        }
    }
}
