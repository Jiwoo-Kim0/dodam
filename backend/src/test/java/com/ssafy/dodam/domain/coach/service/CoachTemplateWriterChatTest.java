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
                new Fact("현재 예상 달성일(비상금 500만 원)", "2028-02-13"),
                new Fact("실행 후 예상 달성일(비상금 500만 원)", "2027-10-15"),
                new Fact("단축(비상금 500만 원)", "121일"),
                new Fact("만기까지 이자 절감(수수료 제외)", "384,000원"),
                new Fact("만기까지 총 이자(현재 → 실행 후)", "1,773,405원 → 1,389,405원"))));

        assertThat(writer.write(facts)).isEqualTo(
                "비상금 500만 원 목표는 현재 2028-02-13에서 실행 후 2027-10-15로 121일 빨라질 것으로 예상돼요. "
                        + "만기까지 낼 이자가 384,000원 줄어들 것으로 예상돼요(중도상환수수료 제외).");
    }

    @Test
    void speaksTheNextMonthOfASpendingForecast() {
        CoachFacts facts = chat(new Section("월 변동 소비 예측(서버 계산)", List.of(
                new Fact("평소 한 달 변동 소비(계절 제외)", "500,000원"),
                new Fact("2026년 10월 예상 변동 소비", "520,000원"),
                new Fact("2026년 10월 주요 변화 요인", "계절 영향 +10,000원"),
                new Fact("2026년 12월 예상 변동 소비", "650,000원"))));

        assertThat(writer.write(facts)).isEqualTo("2026년 10월 변동 소비는 520,000원으로 예상돼요. 가장 큰 변화 요인은 계절 영향 +10,000원이에요.");
    }

    @Test
    void delayNotAchievableAndUnchangedAreSaidWithoutInventingDates() {
        CoachFacts facts = chat(new Section("시뮬레이션 결과(서버 계산)", List.of(
                new Fact("현재 예상 달성일(비상금)", "2028-02-13"),
                new Fact("실행 후 예상 달성일(비상금)", "실행 후에도 달성 불가"),
                new Fact("현재 예상 달성일(여행)", "2027-01-01"),
                new Fact("실행 후 예상 달성일(여행)", "2027-03-02"),
                new Fact("지연(여행)", "60일"))));

        assertThat(writer.write(facts)).isEqualTo(
                "비상금 목표는 실행 후에도 지금 속도로는 달성일을 예상할 수 없어요. "
                        + "여행 목표는 현재 2027-01-01에서 실행 후 2027-03-02로 60일 늦어질 것으로 예상돼요.");

        CoachFacts nowAchievable = chat(new Section("시뮬레이션 결과(서버 계산)", List.of(
                new Fact("현재 예상 달성일(비상금)", "현재 속도로는 달성 불가"),
                new Fact("실행 후 예상 달성일(비상금)", "2028-05-01"))));
        assertThat(writer.write(nowAchievable)).isEqualTo(
                "비상금 목표는 지금 속도로는 달성일을 예상할 수 없지만, 실행 후에는 2028-05-01에 달성할 것으로 예상돼요.");

        CoachFacts unchanged = chat(new Section("시뮬레이션 결과(서버 계산)", List.of(
                new Fact("현재 예상 달성일(비상금)", "2026-09-21"),
                new Fact("실행 후 예상 달성일(비상금)", "2026-09-21"))));
        assertThat(writer.write(unchanged)).isEqualTo("비상금 목표 달성일은 2026-09-21로 변화가 없어요.");
    }

    @Test
    void goalsWithoutDatesAreNotMentionedAndPartialScopeIsSaid() {
        // 대출 추가 상환: 목표 달성일 사실이 없다 → 이자만 말한다("계산 불가"라는 문장이 나가지 않는다)
        CoachFacts prepayment = chat(new Section("시뮬레이션 결과(서버 계산)", List.of(
                new Fact("만기까지 이자 절감(수수료 제외)", "120,000원"))));
        assertThat(writer.write(prepayment)).isEqualTo("만기까지 낼 이자가 120,000원 줄어들 것으로 예상돼요(중도상환수수료 제외).");

        // 복합(소비+대출): 소비 자식의 단독 결과를 범위를 밝히고 말한다
        CoachFacts combined = chat(new Section("시뮬레이션 결과(서버 계산)", List.of(
                new Fact("현재 예상 달성일(비상금)", "2028-02-13"),
                new Fact("실행 후 예상 달성일(비상금)", "2027-10-15"),
                new Fact("단축(비상금)", "121일"),
                new Fact("달성일 계산 범위", "소비 줄이기·저축 변경만 반영(대출 상환·대환은 제외)"),
                new Fact("평가 기간 금융비용 변화", "1,773,405원 → 1,389,405원"))));
        assertThat(writer.write(combined)).isEqualTo(
                "비상금 목표는 현재 2028-02-13에서 실행 후 2027-10-15로 121일 빨라질 것으로 예상돼요. "
                        + CoachTemplateWriter.CHAT_GOAL_SCOPE_PARTIAL);
    }

    @Test
    void relaysTheFirstToolFailureWhenNothingWasComputed() {
        CoachFacts facts = chat(new Section(CoachTemplateWriter.FAILED_SECTION_TITLE, List.of(
                new Fact("소비 줄이기 시뮬레이션", "카페 월평균 소비(38,000원)보다 큰 금액이에요."))));

        assertThat(writer.write(facts)).isEqualTo("요청을 실행하지 못했어요. 카페 월평균 소비(38,000원)보다 큰 금액이에요.");
    }

    @Test
    void answersMonthlyPrepaymentQuestionWithTheNextScheduledPayment() {
        CoachFacts facts = chat(new Section("시뮬레이션 결과(서버 계산)", List.of(
                new Fact("만기까지 이자 절감(수수료 제외)", "136,436원"),
                new Fact("다음 약정 상환일", "2026-10-27"),
                new Fact("다음 회차 예상 납입액", "410,000원 → 380,000원"),
                new Fact("다음 회차 납입액 감소", "30,000원"))));

        assertThat(writer.chat(facts, "매달 얼마 줄어?")).isEqualTo(
                "다음 회차(2026-10-27) 예상 납입액은 410,000원 → 380,000원으로 30,000원 줄어들어요. 이후 회차의 금액은 월별로 다를 수 있어요.");
        assertThat(writer.chat(facts, "이자는 얼마나 아껴?")).isEqualTo(
                "만기까지 낼 이자가 136,436원 줄어들 것으로 예상돼요. 중도상환수수료는 포함되지 않았어요.");
        assertThat(writer.chat(facts, "매달 이자는 얼마 줄어?")).contains("월별 이자 감소액은 이 결과에서 확인할 수 없어요");
        CoachFacts noNextPayment = chat(new Section("시뮬레이션 결과(서버 계산)", List.of(
                new Fact("만기까지 이자 절감(수수료 제외)", "136,436원"))));
        assertThat(writer.chat(noNextPayment, "매달 얼마 줄어?")).contains("다음 회차 납입액은 계산 결과에서 확인할 수 없어요");
        CoachFacts paidOff = chat(new Section("시뮬레이션 결과(서버 계산)", List.of(
                new Fact("추가상환 후 남은 납입 회차", "없음(전액 상환)"))));
        assertThat(writer.chat(paidOff, "매달 얼마 줄어?")).contains("이후 약정 납입 회차가 없어요");
    }

    @Test
    void answersGoalForecastAndDiagnosisInsteadOfOnlyPointingToFacts() {
        CoachFacts goal = chat(new Section("목표 예상 달성일(서버 계산)", List.of(
                new Fact("목표", "비상금"), new Fact("예상 달성일(현재 속도)", "2027-10-15"),
                new Fact("목표일보다 늦음", "30일"))));
        assertThat(writer.chat(goal, "비상금 언제 모아?")).isEqualTo(
                "비상금 목표의 예상 달성일은 2027-10-15로 예상돼요. 목표일보다 30일 늦어요.");
        CoachFacts unreachable = chat(new Section("목표 예상 달성일(서버 계산)", List.of(
                new Fact("목표", "비상금"), new Fact("예상 달성일(현재 속도)", "현재 속도로는 달성 불가"))));
        assertThat(writer.chat(unreachable, "비상금 언제 모아?")).isEqualTo("비상금 목표는 현재 저축 속도로 달성일을 예상하기 어려워요.");

        CoachFacts diagnosis = chat(new Section("최근 진단(서버 판정)", List.of(
                new Fact("종합 상태(서버 판정)", "주의"), new Fact("문제 수", "2건"),
                new Fact("문제 1 (심각도 높음)", "카페 지출 증가"))));
        assertThat(writer.chat(diagnosis, "최근 진단 어때?")).isEqualTo(
                "최근 진단의 종합 상태는 주의로 나왔어요. 우선 살펴볼 항목은 카페 지출 증가예요.");
        CoachFacts healthy = chat(new Section("최근 진단(서버 판정)", List.of(
                new Fact("종합 상태(서버 판정)", "양호"), new Fact("문제 수", "0건"))));
        assertThat(writer.chat(healthy, "최근 진단 어때?")).isEqualTo("최근 진단의 종합 상태는 양호로 나왔어요. 발견된 문제는 없어요.");
    }

    @Test
    void selectsTheMonthTheUserAskedAbout() {
        CoachFacts facts = chat(new Section("월 변동 소비 예측(서버 계산)", List.of(
                new Fact("2026년 10월 예상 변동 소비", "520,000원"),
                new Fact("2026년 11월 예상 변동 소비", "610,000원"))));

        assertThat(writer.chat(facts, "11월 소비 얼마야?")).isEqualTo("2026년 11월 변동 소비는 610,000원으로 예상돼요.");
        assertThat(writer.chat(facts, "12월 소비 얼마야?")).contains("요청하신 달의 예측은 제공되지 않아요");
        assertThat(writer.chat(facts, "2027년 11월 소비는?")).contains("요청하신 달의 예측은 제공되지 않아요");
    }

    @Test
    void fallsBackToFixedSentencesWhenThereIsNothingToSay() {
        assertThat(writer.write(chat())).isEqualTo(CoachTemplateWriter.CHAT_NO_ANSWER);
        // 결과는 있는데 템플릿이 읽는 라벨이 없다(조회 도구만 실행) — 카드로 보라고 안내한다
        CoachFacts diagnosisOnly = chat(new Section("최근 진단", List.of(new Fact("문제 수", "3건"))));
        assertThat(writer.write(diagnosisOnly)).isEqualTo(CoachTemplateWriter.CHAT_SEE_FACTS);
    }
}
