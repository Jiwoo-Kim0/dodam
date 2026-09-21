package com.ssafy.dodam.domain.coach.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * {@code POST /api/v1/coach/chat} 본문(640 A-3, 계획 §1-3).
 *
 * <p>{@code message}·{@code chipId}·{@code context} 중 하나는 있어야 한다(전부 없으면 400 — 서비스가 검사한다. 길이 상한은
 * 설정값이라 애너테이션이 아니라 서비스가 본다). {@code chipId}가 있으면 서버 고정 매핑으로 도구를 바로 실행하고 1차 모델 호출을
 * 생략한다. {@code context}만 있고 문장·칩이 없으면 <b>첫 조언</b>(674)이다 — 사용자가 아직 묻지 않았고 서버가 그 항목에 대한
 * 첫 문장을 만든다. {@code context}는 발견 상세의 진단 문제({@code diagnosisId}+{@code issueId}) 또는 개선안({@code recommendationId})
 * 중 하나이며, 본인 것이 아니면 404다.
 *
 * @param message        사용자 문장. 칩 클릭·첫 조언이면 생략
 * @param chipId         시연 칩 식별자({@code CHIP_SPENDING_CAFE_50K} 등)
 * @param context        화면 맥락(선택). 문장·칩 없이 이것만 오면 첫 조언
 * @param conversationId 이어 갈 대화. 없거나 만료됐으면 새 대화. 맥락이 세션과 다르면 서버가 새 대화를 시작하고 새 id를 돌려준다
 */
public record CoachChatRequest(
        String message,
        String chipId,
        @Valid Context context,
        @Pattern(regexp = "c_[0-9a-f]{16}", message = "conversationId 형식이 아닙니다.") String conversationId
) {

    /**
     * 진단 문제({@code diagnosisId}+{@code issueId}) 또는 개선안({@code recommendationId}) 중 정확히 하나. 반쪽이거나 섞이면 400.
     */
    public record Context(@Positive Long diagnosisId, @Positive Long issueId, @Positive Long recommendationId) {

        public boolean isIssue() {
            return diagnosisId != null && issueId != null && recommendationId == null;
        }

        public boolean isRecommendation() {
            return recommendationId != null && diagnosisId == null && issueId == null;
        }

        public boolean isValid() {
            return isIssue() || isRecommendation();
        }

        /** 세션에 남기는 맥락 키. 다른 항목에서 열면 대화를 새로 시작하는 기준이다(674, 이전 맥락 혼입 방지). */
        public String key() {
            return isRecommendation() ? "recommendation:" + recommendationId : "issue:" + diagnosisId + ":" + issueId;
        }
    }
}
