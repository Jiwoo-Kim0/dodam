package com.ssafy.dodam.domain.coach;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.dodam.domain.coach.dto.CoachFacts;
import com.ssafy.dodam.domain.coach.dto.CoachFacts.Fact;
import com.ssafy.dodam.domain.coach.dto.CoachFacts.Section;
import com.ssafy.dodam.domain.coach.dto.CoachTopic;
import com.ssafy.dodam.domain.coach.service.CoachNumberGuard;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 명세 21 "AI 자체 계산 금지"를 응답 단계에서 강제하는 검사. */
class CoachNumberGuardTest {

    private final CoachNumberGuard guard = new CoachNumberGuard();

    private static CoachFacts facts(String... labelValuePairs) {
        List<Fact> items = new java.util.ArrayList<>();
        for (int i = 0; i < labelValuePairs.length; i += 2) {
            items.add(new Fact(labelValuePairs[i], labelValuePairs[i + 1]));
        }
        return new CoachFacts(CoachTopic.RECOMMENDATION_EXPLANATION, "구독 정리",
                List.of(new Section("예상 효과", items)), List.of());
    }

    @Test
    void acceptsNumbersThatCameFromTheServer() {
        CoachFacts given = facts("월 절감액", "12,000원", "분류 신뢰도", "62.5%");

        assertThat(guard.findUnverifiedNumber("매달 12,000원을 아낄 수 있고 분류 신뢰도는 62.5%입니다.", given)).isEmpty();
        // 구분 기호 유무는 같은 숫자로 본다
        assertThat(guard.findUnverifiedNumber("매달 12000원입니다.", given)).isEmpty();
    }

    @Test
    void rejectsAnnualAmountTheModelComputedItself() {
        // 12,000 × 12 = 144,000. 서버가 연 절감액을 저장하지 않았으므로 모델이 만들어 낸 값이다.
        CoachFacts given = facts("월 절감액", "12,000원");

        assertThat(guard.findUnverifiedNumber("매달 12,000원, 연 144,000원을 아낍니다.", given)).contains("144,000");
    }

    @Test
    void allowsRoundedFormsSoNormalSentencesDoNotFallBack() {
        CoachFacts given = facts("분류 신뢰도", "62.5%");

        assertThat(guard.findUnverifiedNumber("신뢰도는 약 63%입니다.", given)).isEmpty();
        assertThat(guard.findUnverifiedNumber("신뢰도는 62%대입니다.", given)).isEmpty();
        assertThat(guard.findUnverifiedNumber("신뢰도는 75%입니다.", given)).contains("75");
    }

    @Test
    void treatsLeadingZeroAndListNumbersAsSafe() {
        CoachFacts given = facts("진단 완료 시각", "2026-09-16 12:00");

        // "09"와 "9"는 같은 숫자로 본다
        assertThat(guard.findUnverifiedNumber("2026년 9월 16일 기준입니다.", given)).isEmpty();
        // 목록 번호 1~3은 사실 목록에 없어도 허용한다
        assertThat(guard.findUnverifiedNumber("1. 첫째 2. 둘째 3. 셋째", given)).isEmpty();
    }

    @Test
    void ignoresKoreanQuantityWordsAndEmptyText() {
        CoachFacts given = facts("탐지된 문제 수", "2건");

        assertThat(guard.findUnverifiedNumber("문제가 두 가지 있고 절반 정도가 소비 영역입니다.", given)).isEmpty();
        assertThat(guard.findUnverifiedNumber("   ", given)).isEmpty();
    }
}
