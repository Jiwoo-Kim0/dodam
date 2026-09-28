package com.ssafy.dodam.domain.pattern.dto.request;

/**
 * POST /api/v1/patterns/analyze Body. 둘 다 선택이다.
 *
 * @param forceRefresh 오늘 완료된 결과가 있어도 다시 분석한다(군집 모델도 다시 학습). 기본 false
 * @param analysisMode RULE_ONLY | HYBRID. 기본 HYBRID. 값 검증은 {@code AnalysisMode.parseOrDefault}
 */
public record AnalyzePatternRequest(Boolean forceRefresh, String analysisMode) {

    public static AnalyzePatternRequest defaults() {
        return new AnalyzePatternRequest(null, null);
    }

    public boolean force() {
        return Boolean.TRUE.equals(forceRefresh);
    }
}
