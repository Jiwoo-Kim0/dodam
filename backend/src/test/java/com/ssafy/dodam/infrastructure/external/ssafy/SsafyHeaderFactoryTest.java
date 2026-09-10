package com.ssafy.dodam.infrastructure.external.ssafy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SsafyHeaderFactoryTest {

    private static final SsafyFinanceProperties PROPERTIES = new SsafyFinanceProperties(
            "https://finopenapi.ssafy.io", "test-api-key", Duration.ofSeconds(3), Duration.ofSeconds(5)
    );

    // 2026-09-09 18:30:45 Asia/Seoul
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-09-09T09:30:45Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void createsHeaderWithFinanceNetworkConventions() {
        SsafyHeaderFactory factory = new SsafyHeaderFactory(PROPERTIES, FIXED);

        SsafyRequestHeader header = factory.create("inquireMyCreditRating", "user-key-1");

        assertThat(header.apiName()).isEqualTo("inquireMyCreditRating");
        assertThat(header.apiServiceCode()).isEqualTo("inquireMyCreditRating");
        assertThat(header.transmissionDate()).isEqualTo("20260909");
        assertThat(header.transmissionTime()).isEqualTo("183045");
        assertThat(header.institutionCode()).isEqualTo("00100");
        assertThat(header.fintechAppNo()).isEqualTo("001");
        assertThat(header.apiKey()).isEqualTo("test-api-key");
        assertThat(header.userKey()).isEqualTo("user-key-1");
    }

    @Test
    void transactionUniqueNoIsTwentyDigitsPrefixedWithTransmissionDateTime() {
        SsafyHeaderFactory factory = new SsafyHeaderFactory(PROPERTIES, FIXED);

        String uniqueNo = factory.create("inquireCreditRatingList", null).institutionTransactionUniqueNo();

        assertThat(uniqueNo).hasSize(20).matches("\\d{20}").startsWith("20260909183045");
    }

    @Test
    void transactionUniqueNoDiffersBetweenCallsInTheSameSecond() {
        SsafyHeaderFactory factory = new SsafyHeaderFactory(PROPERTIES, FIXED);

        Set<String> numbers = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            numbers.add(factory.create("inquireMyCreditRating", "k").institutionTransactionUniqueNo());
        }

        // 같은 초에 50번 호출해도 난수 일련번호 덕에 사실상 겹치지 않는다
        assertThat(numbers.size()).isGreaterThan(45);
    }
}
