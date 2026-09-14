package com.ssafy.dodam.domain.subscription.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * 미등록 반복결제 후보 한 건(GET /subscriptions/candidates). 거래 원장에서 같은 가맹점·같은 금액이 일정한 간격으로 반복된 묶음이다.
 *
 * @param averageIntervalDays     연속 결제 사이의 평균 일수(소수 1자리)
 * @param estimatedBillingCycle   평균 간격으로 추정한 주기(WEEKLY / MONTHLY / YEARLY)
 * @param suggestedPaymentDay     가장 자주 결제된 일(day of month). WEEKLY는 null
 * @param expectedNextPaymentDate 마지막 결제일에 주기를 더해 오늘 이후로 맞춘 다음 예상 결제일
 * @param cardId                  가장 많이 쓰인 카드. 계좌 결제만이면 null
 * @param categoryCode            거래 카테고리 코드(가장 많이 붙은 것). 미분류면 null
 * @param suggestedCategory       가맹점명이 서비스 카탈로그와 맞으면 그 카테고리, 아니면 null
 * @param matchedServiceCode      가맹점명과 맞은 카탈로그 서비스 코드. 없으면 null
 * @param transactionIds          묶음에 포함된 거래 ID(최신순)
 */
public record RecurringPaymentCandidateResponse(
        String merchantName,
        long amount,
        int occurrences,
        LocalDate firstPaymentDate,
        LocalDate lastPaymentDate,
        double averageIntervalDays,
        String estimatedBillingCycle,
        Integer suggestedPaymentDay,
        LocalDate expectedNextPaymentDate,
        Long cardId,
        String categoryCode,
        String suggestedCategory,
        String matchedServiceCode,
        List<String> transactionIds
) {
}
