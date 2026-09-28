package com.ssafy.dodam.domain.coach.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ssafy.dodam.domain.coach.dto.CoachFacts;
import com.ssafy.dodam.domain.coach.dto.CoachTopic;
import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code POST /api/v1/coach/chat} 응답(640 A-3). {@link CoachMessageResponse}의 필드 이름·의미를 그대로 두고
 * 대화 식별자·도구 결과·추천 질문을 더했다. FE는 3종 코치와 같이 {@code source}로 배지를, {@code facts}로 결과 카드를 그린다.
 *
 * @param conversationId     이 턴이 속한 대화. 다음 요청에 그대로 보내면 이어진다
 * @param toolResults        이 턴에 실행한 도구. {@code simulationId}가 있으면 "자세히"로 기존 시뮬레이션 조회 API를 연다.
 *                           실패한 도구는 {@code error}에 사용자에게 보여도 되는 사유가 있다
 * @param suggestedQuestions 다음 질문 제안. 자유 입력 경로로 보내는 문장이다(칩 매핑이 아니다)
 */
public record CoachChatResponse(
        CoachTopic topic,
        CoachSource source,
        @JsonInclude(JsonInclude.Include.ALWAYS) CoachDegradedReason degradedReason,
        @JsonInclude(JsonInclude.Include.ALWAYS) String model,
        LocalDateTime generatedAt,
        String conversationId,
        String message,
        CoachFacts facts,
        List<ToolResult> toolResults,
        List<String> suggestedQuestions,
        String disclaimer
) {

    public CoachChatResponse {
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
        suggestedQuestions = suggestedQuestions == null ? List.of() : List.copyOf(suggestedQuestions);
    }

    public record ToolResult(
            String tool,
            @JsonInclude(JsonInclude.Include.ALWAYS) Long simulationId,
            @JsonInclude(JsonInclude.Include.ALWAYS) Boolean created,
            @JsonInclude(JsonInclude.Include.ALWAYS) String error
    ) {
    }
}
