package com.ssafy.dodam.domain.pattern.service;

import com.ssafy.dodam.domain.finance.repository.FinanceConnectionRepository;
import com.ssafy.dodam.domain.pattern.dto.request.AnalyzePatternRequest;
import com.ssafy.dodam.domain.pattern.dto.response.AnalyzePatternResult;
import com.ssafy.dodam.domain.pattern.dto.response.ClusterProfileResponse;
import com.ssafy.dodam.domain.pattern.entity.AnalysisMode;
import com.ssafy.dodam.domain.pattern.entity.PatternAnalysis;
import com.ssafy.dodam.domain.pattern.repository.JdbcPatternFeatureReader;
import com.ssafy.dodam.domain.pattern.repository.JdbcPatternFeatureReader.MonthlyPatternRow;
import com.ssafy.dodam.domain.pattern.service.ClusterAssigner.ClusterResult;
import com.ssafy.dodam.domain.pattern.service.PatternAnalysisExecutionStore.Admission;
import com.ssafy.dodam.domain.pattern.service.PatternAnalysisExecutionStore.ClusterAttachment;
import com.ssafy.dodam.domain.pattern.service.PatternAnalysisExecutionStore.Completion;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * POST /api/v1/patterns/analyze (S15P21D105-516). 접수 → 계산 → 완료 순서로, 계산은 트랜잭션·잠금 밖에서 한다.
 *
 * <p><b>선행 조건(명세 "금융 데이터 연동 완료 필요")</b>: 분석 기간에 거래가 한 건도 없으면 접수하지 않는다. 금융망 연결이
 * CONNECTED가 아니면 409 `FINANCE_NOT_CONNECTED`, 연결돼 있는데 데이터가 없으면 409 `PATTERN_INSUFFICIENT_DATA`다.
 * seed 사용자는 `finance_connection` 행이 없지만 SEED 거래가 있으므로 분석할 수 있다(연결 상태보다 데이터 존재를 우선한다).
 *
 * <p><b>실패 처리</b>: 선행 조건 위반·검증 오류({@link BusinessException})는 접수 행을 지우고 그대로 던진다. 그 외 런타임 오류는
 * FAILED로 남기고 던진다. 군집 모델 오류는 {@link ClusterAssigner}가 RULE_ONLY 전환으로 흡수하므로 여기까지 오지 않는다.
 */
@Service
public class PatternAnalysisRunner {

    private final PatternAnalysisExecutionStore store;
    private final JdbcPatternFeatureReader reader;
    private final PatternFeatureCalculator calculator;
    private final ClusterAssigner assigner;
    private final PatternResponseAssembler assembler;
    private final FinanceConnectionRepository connections;
    private final PatternProperties properties;
    private final PatternClock clock;

    public PatternAnalysisRunner(PatternAnalysisExecutionStore store, JdbcPatternFeatureReader reader,
            PatternFeatureCalculator calculator, ClusterAssigner assigner, PatternResponseAssembler assembler,
            FinanceConnectionRepository connections, PatternProperties properties, PatternClock clock) {
        this.store = store;
        this.reader = reader;
        this.calculator = calculator;
        this.assigner = assigner;
        this.assembler = assembler;
        this.connections = connections;
        this.properties = properties;
        this.clock = clock;
    }

    public AnalyzePatternResult run(Long userId, AnalyzePatternRequest request) {
        AnalysisMode requestedMode = AnalysisMode.parseOrDefault(request.analysisMode());
        boolean forceRefresh = request.force();
        int lookbackMonths = properties.lookbackMonths();
        PatternWindow window = PatternWindow.endingBefore(clock.today(), lookbackMonths);

        List<MonthlyPatternRow> rows = reader.readUserMonthly(userId, window.startDate(), window.endDate());
        requireAnalyzableData(userId, rows);

        Admission admission = store.admit(userId, requestedMode, forceRefresh, lookbackMonths);
        if (admission.cached()) {
            return new AnalyzePatternResult(assembler.toResponse(admission.analysis()), true);
        }
        Long analysisId = admission.analysis().getId();
        try {
            PatternFeatures features = calculator.calculate(window, rows, reader.readLiquidity(userId),
                    reader.readProfileMonthlyIncome(userId));
            if (!features.hasData()) {
                throw new BusinessException(ErrorCode.PATTERN_INSUFFICIENT_DATA);
            }
            ClusterAssigner.Outcome outcome = assigner.evaluate(requestedMode, features, forceRefresh);
            Completion completion = new Completion(features, assembler.toJson(assembler.summarize(requestedMode, features, outcome)),
                    outcome.assigned() ? attachment(outcome.cluster()) : null);
            PatternAnalysis completed = store.complete(userId, analysisId, completion);
            return new AnalyzePatternResult(assembler.toResponse(completed), false);
        } catch (BusinessException precondition) {
            cleanup(() -> store.abandon(userId, analysisId), precondition);
            throw precondition;
        } catch (RuntimeException failure) {
            cleanup(() -> store.fail(userId, analysisId, assembler.failureSummary(requestedMode, window, failure)), failure);
            throw failure;
        }
    }

    private void requireAnalyzableData(Long userId, List<MonthlyPatternRow> rows) {
        boolean hasData = rows.stream().anyMatch(row -> row.transactionCount() > 0);
        if (hasData) {
            return;
        }
        boolean connected = connections.findByUserId(userId).map(connection -> connection.isConnected()).orElse(false);
        throw new BusinessException(connected ? ErrorCode.PATTERN_INSUFFICIENT_DATA : ErrorCode.FINANCE_NOT_CONNECTED);
    }

    private ClusterAttachment attachment(ClusterResult cluster) {
        ClusterProfileResponse profile = assembler.clusterProfile(cluster);
        return new ClusterAttachment(profile.modelVersion(), profile.clusterId(), profile.clusterLabel(),
                profile.featureWindowMonths(), profile.sampleSize(), profile.confidence(), assembler.toJson(profile));
    }

    private static void cleanup(Runnable action, RuntimeException cause) {
        try {
            action.run();
        } catch (RuntimeException cleanupFailure) {
            cause.addSuppressed(cleanupFailure);
        }
    }
}
