package com.ssafy.dodam.domain.deposit.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 비교 후보 한 건.
 *
 * @param representativeRate 조건에 맞는 옵션 기본금리들의 중앙값. 상품마다 옵션 수가 달라도 동일 가중이 되게 한다.
 * @param disclosedRate 대표값에 가장 가까운 근거 옵션의 공시 기본금리
 * @param disclosedPreferredRate 근거 옵션의 우대금리. 판정에는 쓰지 않고 참고로만 반환한다.
 * @param term 채택한 옵션의 실제 개월 수. 요청·보유 기간과 다를 수 있어 근거로 함께 반환한다.
 * @param unmatchedConditions <b>이 후보에만</b> 해당하는 미일치 조건. 코드명 오름차순이며 없으면 빈 목록이다.
 *     응답 상위의 같은 이름 필드는 전체 후보의 합집합이라, 어떤 후보가 어긋났는지는 여기서만 알 수 있다.
 */
record DepositRateCandidate(
        Long productId,
        String productName,
        String institutionName,
        String sourceType,
        BigDecimal representativeRate,
        BigDecimal disclosedRate,
        BigDecimal disclosedPreferredRate,
        Integer term,
        String rateType,
        LocalDateTime syncedAt,
        List<String> unmatchedConditions
) {
}
