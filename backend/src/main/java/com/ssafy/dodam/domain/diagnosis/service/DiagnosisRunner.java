package com.ssafy.dodam.domain.diagnosis.service;

import com.ssafy.dodam.domain.diagnosis.dto.DiagnosisResponse;
import org.springframework.stereotype.Service;

@Service
public class DiagnosisRunner {
    private final DiagnosisExecutionStore store;
    private final DiagnosisEvaluator evaluator;

    public DiagnosisRunner(DiagnosisExecutionStore store, DiagnosisEvaluator evaluator) {
        this.store = store;
        this.evaluator = evaluator;
    }

    public Result run(Long userId, Long goalId, boolean forceRefresh) {
        var admission = store.admit(userId, goalId, forceRefresh);
        if (admission.cached()) {
            return new Result(DiagnosisResponse.from(admission.diagnosis()), true);
        }
        Long id = admission.diagnosis().getDiagnosisId();
        try {
            var result = evaluator.evaluate(admission.diagnosis());
            return new Result(DiagnosisResponse.from(store.complete(userId, id, result)), false);
        } catch (RuntimeException failure) {
            try {
                store.abandon(userId, id);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    public record Result(DiagnosisResponse diagnosis, boolean cached) { }
}
