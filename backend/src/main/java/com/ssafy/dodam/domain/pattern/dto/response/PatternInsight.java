package com.ssafy.dodam.domain.pattern.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 군집·동년배 위치에서 만든 한 줄 문장(632). 코드는 FE 아이콘·강조 분기와 AI 코치(589) 근거 키로 쓰고, 숫자 근거를 함께 실어
 * FE가 문장을 다시 만들 수 있게 한다. 문장은 서버 템플릿(한국어, '-어요')이며 임계값은 {@code PatternInsightPolicy}에 있다.
 *
 * @param code       CLUSTER_TOP_DECILE_SPENDING, CLUSTER_BOTTOM_DECILE_SAVING, CLUSTER_VOLATILITY_HIGH, PEER_TOP_DECILE_SPENDING,
 *                   PEER_ABOVE_MEDIAN_SPENDING, PEER_TOP_QUARTILE_SAVING, PEER_VOLATILITY_HIGH, PEER_CLUSTER_SHARE
 * @param feature    근거 특징 키(`spendingToIncomeRatio` 등). 군집 구성비 문장이면 null
 * @param clusterId  근거 군집 번호. 특징 문장이면 null
 * @param percentile 근거 백분위(0~100). 구성비 문장이면 null
 * @param ratio      근거 비율(0~1). 구성비 문장에만 있다
 * @param message    표시 문장
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PatternInsight(
        String code,
        String feature,
        Integer clusterId,
        Double percentile,
        Double ratio,
        String message
) {
}
