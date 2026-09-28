package com.ssafy.dodam.domain.coach;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.dodam.domain.coach.dto.CoachTopic;
import com.ssafy.dodam.domain.coach.service.CoachPromptFactory;
import org.junit.jupiter.api.Test;

/** 문구 계약(S15P21D105-604)이 프롬프트에 실제로 들어 있는지. 운영에서 손으로 재본 것을 고정한다. */
class CoachPromptFactoryTest {

    private final CoachPromptFactory prompts = new CoachPromptFactory();

    @Test
    void lengthLimitsMatchTheCopyContract() {
        assertThat(prompts.maxMessageLength(CoachTopic.FINANCIAL_SUMMARY)).isEqualTo(150);
        assertThat(prompts.maxMessageLength(CoachTopic.INEFFICIENCY_EXPLANATION)).isEqualTo(80);
        assertThat(prompts.maxMessageLength(CoachTopic.RECOMMENDATION_EXPLANATION)).isEqualTo(80);
    }

    @Test
    void everyTopicForbidsInternalTermsAndFormalEndings() {
        for (CoachTopic topic : CoachTopic.values()) {
            assertThat(prompts.system(topic))
                    .contains("'-어요'로 끝낸다")
                    .contains("'서버'")
                    .contains("영문 코드");
        }
    }

    @Test
    void summaryIsToldNotToChainIssueTitles() {
        // 2026-09-17 운영 실측에서 "목표 달성 계획 점검과 기타 소비가 늘었어요"라는 비문이 나왔다.
        // 문제 제목이 이미 완결 문장이라 접속사로 엮으면 깨진다.
        assertThat(prompts.system(CoachTopic.FINANCIAL_SUMMARY)).contains("제목을 그대로 이어 붙이지 않는다");
    }

    @Test
    void adviceTopicsAreToldToStopRepeatingThemselves() {
        // 추천 설명이 같은 말을 두 번 해 102자가 됐다(상한 80자)
        assertThat(prompts.system(CoachTopic.RECOMMENDATION_EXPLANATION)).contains("두 번 쓰지 않는다");
        assertThat(prompts.system(CoachTopic.INEFFICIENCY_EXPLANATION)).contains("되풀이하지 않는다");
    }
}
