package com.ssafy.dodam.domain.coach.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * {@code POST /api/v1/coach/chat} 본문(640 A-3, 계획 §1-3).
 *
 * <p>{@code message}와 {@code chipId} 중 하나는 있어야 한다(둘 다 없으면 400 — 서비스가 검사한다. 길이 상한은 설정값이라
 * 애너테이션이 아니라 서비스가 본다). {@code chipId}가 있으면 서버 고정 매핑으로 도구를 바로 실행하고 1차 모델 호출을
 * 생략한다. {@code context}는 발견 상세에서 열었을 때의 진단 문제로, 본인 것이 아니면 404다.
 *
 * @param message        사용자 문장. 칩 클릭이면 생략 가능
 * @param chipId         시연 칩 식별자({@code CHIP_SPENDING_CAFE_50K} 등)
 * @param context        화면 맥락(선택)
 * @param conversationId 이어 갈 대화. 없거나 만료됐으면 새 대화
 */
public record CoachChatRequest(
        String message,
        String chipId,
        @Valid Context context,
        @Pattern(regexp = "c_[0-9a-f]{16}", message = "conversationId 형식이 아닙니다.") String conversationId
) {

    public record Context(@Positive Long diagnosisId, @Positive Long issueId) {

        public boolean isComplete() {
            return diagnosisId != null && issueId != null;
        }
    }
}
