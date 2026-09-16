package com.ssafy.dodam.domain.coach;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.coach.dto.CoachFacts;
import com.ssafy.dodam.domain.coach.dto.CoachFacts.Fact;
import com.ssafy.dodam.domain.coach.dto.CoachFacts.Section;
import com.ssafy.dodam.domain.coach.dto.CoachTopic;
import com.ssafy.dodam.domain.coach.service.CoachCacheKey;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 캐시 무효화는 TTL이 아니라 이 키가 한다. 서버 계산값이 바뀌면 키가 달라져 자동으로 다시 생성된다.
 */
class CoachCacheKeyTest {

    private final CoachCacheKey keys = new CoachCacheKey(new ObjectMapper());

    private static CoachFacts facts(String value) {
        return new CoachFacts(CoachTopic.FINANCIAL_SUMMARY, "요약",
                List.of(new Section("진단 개요", List.of(new Fact("탐지된 문제 수", value)))), List.of());
    }

    @Test
    void sameFactsAndUserProduceTheSameKey() {
        assertThat(keys.of(1L, facts("2건"))).isEqualTo(keys.of(1L, facts("2건")));
    }

    @Test
    void anyChangeInServerComputedValuesChangesTheKey() {
        // 재진단·추천 상태 변경·패턴 재분석이 모두 facts를 바꾸므로 별도 무효화 코드가 필요 없다
        assertThat(keys.of(1L, facts("2건"))).isNotEqualTo(keys.of(1L, facts("3건")));
    }

    @Test
    void differentUsersNeverShareAnEntry() {
        assertThat(keys.of(1L, facts("2건"))).isNotEqualTo(keys.of(2L, facts("2건")));
    }

    @Test
    void keyCarriesUserAndTopicForOperability() {
        assertThat(keys.of(7L, facts("2건"))).startsWith("coach:7:FINANCIAL_SUMMARY:");
    }

    @Test
    void returnsNullInsteadOfFailingWhenItCannotBuildAKey() {
        // 키를 못 만드는 것은 기능 실패가 아니다. 호출자는 캐시를 건너뛰고 정상 경로로 간다.
        assertThat(keys.of(null, facts("2건"))).isNull();
        assertThat(keys.of(1L, null)).isNull();
    }
}
