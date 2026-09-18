package com.ssafy.dodam.domain.coach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 640 A-2: 모델이 만든 인자는 신뢰하지 않는다 — 형식·범위·코드 집합을 서버가 다시 검사한다. */
class CoachToolArgumentsTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void parsesAmountsFromNumbersAndKoreanFormattedStrings() {
        CoachToolArguments numeric = CoachToolArguments.parse(json, "{\"monthlyReduction\": 50000}");
        assertThat(numeric.requiredAmount("monthlyReduction", "줄일 금액")).isEqualTo(50_000L);
        CoachToolArguments text = CoachToolArguments.parse(json, "{\"monthlyReduction\": \"50,000원\"}");
        assertThat(text.amount("monthlyReduction", "줄일 금액")).isEqualTo(50_000L);
        CoachToolArguments decimal = CoachToolArguments.parse(json, "{\"monthlyReduction\": 50000.7}");
        assertThat(decimal.amount("monthlyReduction", "줄일 금액")).isEqualTo(50_000L);
        assertThat(CoachToolArguments.parse(json, "{}").amount("monthlyReduction", "줄일 금액")).isNull();
        assertThat(CoachToolArguments.parse(json, "").has("monthlyReduction")).isFalse();
    }

    @Test
    void rejectsNonPositiveOversizedOrUnreadableAmounts() {
        assertThatThrownBy(() -> CoachToolArguments.parse(json, "{\"monthlyReduction\": 0}").amount("monthlyReduction", "줄일 금액"))
                .isInstanceOf(CoachToolArgumentException.class).hasMessageContaining("0보다 큰");
        assertThatThrownBy(() -> CoachToolArguments.parse(json, "{\"monthlyReduction\": 100000001}").amount("monthlyReduction", "줄일 금액"))
                .isInstanceOf(CoachToolArgumentException.class).hasMessageContaining("1억");
        assertThatThrownBy(() -> CoachToolArguments.parse(json, "{\"monthlyReduction\": \"오만원\"}").amount("monthlyReduction", "줄일 금액"))
                .isInstanceOf(CoachToolArgumentException.class).hasMessageContaining("숫자");
        assertThatThrownBy(() -> CoachToolArguments.parse(json, "{}").requiredAmount("monthlyReduction", "줄일 금액"))
                .isInstanceOf(CoachToolArgumentException.class).hasMessageContaining("알려 주세요");
        assertThatThrownBy(() -> CoachToolArguments.parse(json, "not json")).isInstanceOf(CoachToolArgumentException.class);
        assertThatThrownBy(() -> CoachToolArguments.parse(json, "[1,2]")).isInstanceOf(CoachToolArgumentException.class);
    }

    @Test
    void codesAreUppercasedAndMustBelongToTheAllowedSet() {
        Set<String> allowed = CoachToolRegistry.SPENDING_CATEGORY_CODE_SET;
        assertThat(CoachToolArguments.parse(json, "{\"category\": \"cafe\"}").code("category", allowed, "카테고리")).isEqualTo("CAFE");
        assertThat(CoachToolArguments.parse(json, "{\"category\": \"public transport\"}").code("category", allowed, "카테고리"))
                .isEqualTo("PUBLIC_TRANSPORT");
        assertThat(CoachToolArguments.parse(json, "{}").code("category", allowed, "카테고리")).isNull();
        // 637 실측: 설명 없는 스키마에서 모델이 한글을 넣었다. 코드 집합 밖이면 실행하지 않는다
        assertThatThrownBy(() -> CoachToolArguments.parse(json, "{\"category\": \"카페\"}").code("category", allowed, "카테고리"))
                .isInstanceOf(CoachToolArgumentException.class).hasMessageContaining("카페").hasMessageContaining("CAFE");
    }

    @Test
    void monthsAndItemsAreBounded() {
        assertThat(CoachToolArguments.parse(json, "{\"durationMonths\": 12}").months("durationMonths")).isEqualTo(12);
        assertThat(CoachToolArguments.parse(json, "{}").months("durationMonths")).isNull();
        assertThatThrownBy(() -> CoachToolArguments.parse(json, "{\"durationMonths\": 0}").months("durationMonths"))
                .isInstanceOf(CoachToolArgumentException.class);
        assertThatThrownBy(() -> CoachToolArguments.parse(json, "{\"durationMonths\": 121}").months("durationMonths"))
                .isInstanceOf(CoachToolArgumentException.class);
        CoachToolArguments combined = CoachToolArguments.parse(json,
                "{\"scenarios\": [{\"type\": \"SPENDING\"}, 3, {\"type\": \"SAVING\"}]}");
        assertThat(combined.items("scenarios")).hasSize(2);
        assertThat(combined.items("missing")).isEmpty();
        assertThat(combined.items("scenarios").get(1).text("type")).isEqualTo("SAVING");
    }
}
