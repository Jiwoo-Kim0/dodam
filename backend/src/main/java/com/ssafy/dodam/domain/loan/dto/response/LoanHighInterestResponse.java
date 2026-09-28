package com.ssafy.dodam.domain.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 고금리 점검 대상 목록.
 *
 * <p>{@code rateSpreadThreshold}는 서비스 초기 임계값이며 통계적으로 검증된 기준이 아니다. 기획안의 α는
 * "추후 결정"으로만 적혀 있어 시연 데이터의 편차 분포를 근거로 정했다.
 *
 * <p>{@code loans}에는 점검 대상만 담고 판정 불가 대출은 {@code unavailableLoans}로 분리한다. 세 건수와
 * {@code complete}로 빈 결과의 의미를 구분한다. 대상이 없는 것과 전부 판정 불가인 것은 다른 상태다.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record LoanHighInterestResponse(
        LocalDate asOfDate,
        List<String> assumptions,
        boolean complete,
        List<String> unavailableReasons,
        List<LoanUnavailableResponse> unavailableLoans,
        String thresholdMode,
        BigDecimal rateSpreadThreshold,
        String referenceMethod,
        int evaluatedCount,
        int highInterestCount,
        int unavailableCount,
        List<LoanHighInterestItemResponse> loans
) {
}
