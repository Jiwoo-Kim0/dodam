package com.ssafy.dodam.domain.pattern.entity;

import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.util.Locale;

/**
 * 명세 3 `analysisMode`. RULE_ONLY는 규칙 기반 지표만, HYBRID는 규칙 지표에 소비 유형 군집 분류를 더한다.
 *
 * <p>요청 기본값은 HYBRID다. HYBRID로 요청해도 군집 표본 부족·신뢰도 부족·모델 실패면 RULE_ONLY로 자동 전환하고
 * 그 사유를 {@code featureSummary.fallbackReason}에 남긴다(명세 "군집 표본 부족·모델 실패 시 RULE_ONLY 자동 전환").
 */
public enum AnalysisMode {
    RULE_ONLY,
    HYBRID;

    public static AnalysisMode parseOrDefault(String value) {
        if (value == null || value.isBlank()) {
            return HYBRID;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("analysisMode는 RULE_ONLY 또는 HYBRID여야 합니다.");
        }
    }
}
