package com.ssafy.dodam.domain.loan.service;

import java.math.BigInteger;
import java.util.List;
import java.util.function.ToLongFunction;

/** 회차와 대출 합계를 정확히 누적한 뒤 응답의 Long 범위를 검사한다. */
final class LoanAnalysisAmounts {
    static final String OUT_OF_RANGE = "AGGREGATE_AMOUNT_OUT_OF_RANGE";

    private LoanAnalysisAmounts() {
    }

    static <T> Long sum(List<T> items, ToLongFunction<T> amount) {
        BigInteger total = BigInteger.ZERO;
        for (T item : items) {
            total = total.add(BigInteger.valueOf(amount.applyAsLong(item)));
        }
        return total.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 ? null : total.longValueExact();
    }
}
