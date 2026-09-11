package com.ssafy.dodam.domain.savings.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.function.ToLongFunction;

final class SavingsAmounts {
    private SavingsAmounts() {
    }

    static Long amount(BigDecimal value) {
        try {
            return value.longValueExact();
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    static <T> Long sum(Collection<T> rows, ToLongFunction<T> value) {
        return amount(rows.stream().map(row -> BigDecimal.valueOf(value.applyAsLong(row)))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    static BigDecimal percent(Long amount, Long income) {
        return amount == null || income == null || income <= 0 ? null
                : BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(income), 2, RoundingMode.HALF_UP);
    }
}

