package com.ssafy.dodam.domain.coach;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.coach.dto.CoachFacts.Fact;
import com.ssafy.dodam.domain.coach.service.CoachEvidenceReader;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 스키마가 고정되지 않은 JSON 컬럼에서 무엇이 프롬프트로 나가는지. 진단 규칙이 늘어나 키가 추가돼도
 * 식별자가 새어 나가지 않아야 한다.
 */
class CoachEvidenceReaderTest {

    private final ObjectMapper json = new ObjectMapper();
    private final CoachEvidenceReader reader = new CoachEvidenceReader();

    private List<Fact> read(String raw) throws Exception {
        return reader.read(null, json.readTree(raw));
    }

    @Test
    void dropsInternalIdentifiersThatDiagnosisEvaluatorAdds() throws Exception {
        List<Fact> facts = read("""
                {"ruleKey": "GOAL_SAVING_SHORTFALL", "targetId": 12, "forecastId": 30, "remaining": 1000000}
                """);

        assertThat(facts).extracting(Fact::label).containsExactly("ruleKey", "remaining");
        assertThat(facts).extracting(Fact::value).contains("1,000,000");
    }

    @Test
    void dropsPersonalKeysButKeepsCategoryAndProductNames() throws Exception {
        List<Fact> facts = read("""
                {"email": "a@b.com", "accountNo": "1234567890", "userName": "김도담",
                 "categoryName": "외식", "productName": "청년적금"}
                """);

        assertThat(facts).extracting(Fact::label).containsExactly("categoryName", "productName");
    }

    @Test
    void dropsNumbersTooLongToBeAnAmount() throws Exception {
        // 11자리 정수는 개인 재무 금액으로 비현실적이다(= 100억 원). 계좌번호가 숫자로 들어온 경우를 막는다.
        List<Fact> facts = read("""
                {"value": 12345678901, "amount": 9999999999}
                """);

        assertThat(facts).extracting(Fact::label).containsExactly("amount");
    }

    @Test
    void flattensNestedValuesWithPathLabelsAndCaps() throws Exception {
        List<Fact> facts = reader.read("GOAL", json.readTree("""
                {"metrics": {"ratio": 0.6234, "over": true}, "months": ["2026-07", "2026-08", "2026-09", "2026-10"]}
                """));

        assertThat(facts).contains(new Fact("GOAL.metrics.ratio", "0.6234"), new Fact("GOAL.metrics.over", "예"));
        // 배열은 앞쪽 3개까지만 — 프롬프트 길이가 곧 비용이다
        assertThat(facts).extracting(Fact::label)
                .contains("GOAL.months[1]", "GOAL.months[3]").doesNotContain("GOAL.months[4]");
    }

    @Test
    void returnsEmptyForMissingOrEmptyNodes() throws Exception {
        assertThat(reader.read(null, null)).isEmpty();
        assertThat(read("null")).isEmpty();
        assertThat(read("{}")).isEmpty();
    }
}
