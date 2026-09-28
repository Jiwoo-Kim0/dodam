package com.ssafy.dodam.domain.cashflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.dodam.domain.cashflow.dto.CashflowForecastPeriod;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CashflowRequestHashTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 9, 10);

    @Test
    void sameRequestProducesSameHash() {
        assertThat(hash(1L, BASE_DATE, 90, "v1")).isEqualTo(hash(1L, BASE_DATE, 90, "v1"));
    }

    @Test
    void hashIsSixtyFourHexCharactersToFitTheColumn() {
        assertThat(hash(1L, BASE_DATE, 90, "v1")).matches("[0-9a-f]{64}");
    }

    @Test
    void everyInputChangesTheHash() {
        String reference = hash(1L, BASE_DATE, 90, "v1");

        assertThat(hash(2L, BASE_DATE, 90, "v1")).isNotEqualTo(reference);
        assertThat(hash(1L, BASE_DATE.plusDays(1), 90, "v1")).isNotEqualTo(reference);
        assertThat(hash(1L, BASE_DATE, 91, "v1")).isNotEqualTo(reference);
        assertThat(hash(1L, BASE_DATE, 90, "v2")).isNotEqualTo(reference);
    }

    /** 길이 접두 인코딩이 없으면 자릿수가 다른 조합이 같은 입력 문자열로 합쳐질 수 있다. */
    @Test
    void adjacentFieldsDoNotCollide() {
        assertThat(hash(11L, BASE_DATE, 9, "v1")).isNotEqualTo(hash(1L, BASE_DATE, 19, "v1"));
        assertThat(hash(1L, BASE_DATE, 9, "1v1")).isNotEqualTo(hash(1L, BASE_DATE, 91, "v1"));
    }

    private String hash(Long userId, LocalDate baseDate, int horizonDays, String version) {
        return CashflowRequestHash.of(userId, new CashflowForecastPeriod(baseDate, horizonDays), version);
    }
}
