package com.ssafy.dodam.domain.pattern.dto.response;

/**
 * POST /api/v1/patterns/analyze 응답. 새로 완료했으면 201·`cached=false`, 오늘 완료된 결과를 재사용했으면 200·`cached=true`.
 */
public record AnalyzePatternResult(PatternAnalysisResponse analysis, boolean cached) {
}
