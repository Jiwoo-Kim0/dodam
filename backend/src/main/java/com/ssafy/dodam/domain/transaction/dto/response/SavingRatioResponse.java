package com.ssafy.dodam.domain.transaction.dto.response;

/**
 * 소비 및 저축 비율(GET /spending/saving-ratio). 금액 단위는 원, 비율은 %(소수 첫째 자리).
 *
 * @param income                     비율의 분모. `incomeSource`가 어디서 왔는지 알려준다
 * @param incomeSource               PROFILE(금융 기본정보 `monthly_income`) / TRANSACTIONS(그 달 INCOME 거래 합) / NONE(둘 다 없음, 비율은 null)
 * @param spending                   그 달 소비(506 정의)
 * @param savingTransfer             소비에서 제외한 저축성 지출(NON_SPENDING) 합. 참고용
 * @param remaining                  income − spending. 음수면 소득보다 더 썼다
 * @param spendingRatioPercent       spending / income
 * @param savingRatioPercent         remaining / income (기획안의 "저축률")
 * @param targetSpendingRatioPercent 활성 소비목표(`spending_goal.monthly_budget` 합) / income. 목표가 없거나 소득이 없으면 null
 * @param targetSavingRatioPercent   100 − targetSpendingRatioPercent
 * @param exceedsTarget              소비 비율이 목표 비율을 넘었는지. 목표가 없으면 null
 */
public record SavingRatioResponse(
        String yearMonth,
        long income,
        String incomeSource,
        long spending,
        long savingTransfer,
        long remaining,
        Double spendingRatioPercent,
        Double savingRatioPercent,
        Double targetSpendingRatioPercent,
        Double targetSavingRatioPercent,
        Boolean exceedsTarget
) {
}
