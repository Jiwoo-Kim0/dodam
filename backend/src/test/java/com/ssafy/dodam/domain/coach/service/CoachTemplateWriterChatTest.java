package com.ssafy.dodam.domain.coach.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.dodam.domain.coach.dto.CoachFacts;
import com.ssafy.dodam.domain.coach.dto.CoachFacts.Fact;
import com.ssafy.dodam.domain.coach.dto.CoachFacts.Section;
import com.ssafy.dodam.domain.coach.dto.CoachTopic;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 640 A-3: AI 없이도 도구 결과를 "현재 A → 실행 후 B, N일 단축"으로 말한다. 값은 전부 facts의 서버 포맷 문자열이다. */
class CoachTemplateWriterChatTest {

    private final CoachTemplateWriter writer = new CoachTemplateWriter();

    private static CoachFacts chat(Section... sections) {
        return new CoachFacts(CoachTopic.CHAT, "복합 시뮬레이션", List.of(sections), List.of());
    }

    @Test
    void speaksGoalDatesAndInterestSavingFromTheFacts() {
        CoachFacts facts = chat(new Section("시뮬레이션 결과(서버 계산)", List.of(
                new Fact("시뮬레이션", "복합 시뮬레이션 (id 88)"),
                new Fact("현재 예상 달성일(비상금 500만 원)", "2028-02-13"),
                new Fact("실행 후 예상 달성일(비상금 500만 원)", "2027-10-15"),
                new Fact("단축(비상금 500만 원)", "121일"),
                new Fact("만기까지 이자 절감(수수료 제외)", "384,000원"),
                new Fact("만기까지 총 이자(현재 → 실행 후)", "1,773,405원 → 1,389,405원"))));

        assertThat(writer.write(facts)).isEqualTo(
                "비상금 500만 원 목표는 현재 2028-02-13에서 실행 후 2027-10-15로 121일 빨라질 것으로 예상돼요. "
                        + "만기까지 이자를 384,000원 아낄 수 있어요.");
    }

    @Test
    void delayAndNotComputableAreSaidWithoutInventingDates() {
        CoachFacts facts = chat(new Section("시뮬레이션 결과(서버 계산)", List.of(
                new Fact("현재 예상 달성일(비상금)", "2028-02-13"),
                new Fact("실행 후 예상 달성일(비상금)", "계산 불가"),
                new Fact("현재 예상 달성일(여행)", "2027-01-01"),
                new Fact("실행 후 예상 달성일(여행)", "2027-03-02"),
                new Fact("지연(여행)", "60일"))));

        assertThat(writer.write(facts)).isEqualTo(
                "비상금 목표는 현재 2028-02-13에서 실행 후 계산 불가로 예상돼요. "
                        + "여행 목표는 현재 2027-01-01에서 실행 후 2027-03-02로 60일 늦어질 것으로 예상돼요.");
    }

    @Test
    void relaysTheFirstToolFailureWhenNothingWasComputed() {
        CoachFacts facts = chat(new Section(CoachTemplateWriter.FAILED_SECTION_TITLE, List.of(
                new Fact("소비 줄이기 시뮬레이션", "카페 월평균 소비(38,000원)보다 큰 금액이에요."))));

        assertThat(writer.write(facts)).isEqualTo("요청을 실행하지 못했어요. 카페 월평균 소비(38,000원)보다 큰 금액이에요.");
    }

    @Test
    void fallsBackToFixedSentencesWhenThereIsNothingToSay() {
        assertThat(writer.write(chat())).isEqualTo(CoachTemplateWriter.CHAT_NO_ANSWER);
        // 결과는 있는데 템플릿이 읽는 라벨이 없다(조회 도구만 실행) — 카드로 보라고 안내한다
        CoachFacts diagnosisOnly = chat(new Section("최근 진단", List.of(new Fact("문제 수", "3건"))));
        assertThat(writer.write(diagnosisOnly)).isEqualTo(CoachTemplateWriter.CHAT_SEE_FACTS);
    }
}
