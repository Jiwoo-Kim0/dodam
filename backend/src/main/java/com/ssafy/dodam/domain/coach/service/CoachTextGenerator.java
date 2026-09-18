package com.ssafy.dodam.domain.coach.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * 생성형 AI 호출 포트. 구현은 {@code infrastructure.external.ai}에 있다.
 *
 * <p>도메인이 OpenAI를 직접 알지 않게 분리한다. 공급자를 바꿔도 {@code CoachService}의 실패 처리·숫자
 * 검증 흐름은 그대로다. 테스트는 이 인터페이스만 대체해 "호출 실패 → 템플릿" 경로를 검증한다.
 *
 * <p>(640 A-1) {@link #chat}은 "도리에게 묻기"용 도구 호출 대화다. 모델은 <b>도구를 고르고 인자를 채우고 문장을 쓰는 것</b>만 하고,
 * 도구 실행(시뮬레이션·조회)과 숫자 계산은 전부 서버가 한다. 도구 스키마에 사용자 식별자·계좌번호 원문은 넣지 않는다.
 */
public interface CoachTextGenerator {

    /** 설정이 갖춰져 실제 호출이 가능한 상태인지. false면 {@code CoachService}는 호출 없이 템플릿으로 간다. */
    boolean isAvailable();

    /** 문장을 쓴 모델 이름. 응답의 {@code model}에 그대로 실린다. */
    String modelName();

    /**
     * @return 모델이 쓴 본문
     * @throws CoachGenerationException 타임아웃·연결 실패·비정상 응답 등 외부 호출에서 발생한 모든 실패.
     *         호출자는 이 예외 하나만 처리하면 되며, 공급자별 예외 타입이 도메인으로 새지 않는다
     */
    String generate(String systemPrompt, String userPrompt);

    /**
     * 도구 정의를 함께 보내는 대화 한 턴(640 A-1). 모델이 도구를 골랐으면 {@link ChatTurn#toolCalls()}에, 문장으로 답했으면
     * {@link ChatTurn#content()}에 온다(둘 다 올 수도 있다). 도구 결과를 되돌려 보낼 때는 {@link ChatMessage#tool} 메시지를 붙여 다시 부른다.
     *
     * @param messages system/user/assistant/tool 메시지. 순서 그대로 보낸다
     * @param tools    도구 정의. 비어 있으면 도구 없이 평문 대화다
     * @param toolChoice 도구 선택 강제 방식. {@link ToolChoice#auto()}가 기본
     * @throws CoachGenerationException {@link #generate}와 같은 실패 규약
     */
    ChatTurn chat(List<ChatMessage> messages, List<ToolDefinition> tools, ToolChoice toolChoice);

    /**
     * 대화 메시지. {@code assistant} 메시지는 이전 턴의 {@code toolCalls}를 그대로 되돌려 보내야 하고, {@code tool} 메시지는
     * 어느 호출에 대한 결과인지 {@code toolCallId}로 가리킨다.
     */
    record ChatMessage(String role, String content, List<ToolCall> toolCalls, String toolCallId) {

        public ChatMessage {
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException("메시지 역할이 필요합니다.");
            }
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }

        public static ChatMessage system(String content) {
            return new ChatMessage("system", content, null, null);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content, null, null);
        }

        /** 모델의 이전 답(도구 호출 포함). 도구 결과를 보낼 때 대화 이력에 그대로 넣는다. */
        public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
            return new ChatMessage("assistant", content, toolCalls, null);
        }

        /** 서버가 실행한 도구 결과. {@code content}는 서버가 포맷한 사실 문자열(JSON 또는 텍스트)이다. */
        public static ChatMessage tool(String toolCallId, String content) {
            if (toolCallId == null || toolCallId.isBlank()) {
                throw new IllegalArgumentException("도구 결과에는 tool_call_id가 필요합니다.");
            }
            return new ChatMessage("tool", content, null, toolCallId);
        }
    }

    /**
     * 도구 정의(OpenAI function 형식). {@code parameters}는 JSON Schema 객체이며 인자는 enum·description으로 고정한다 —
     * 637 실측에서 설명 없는 문자열 인자에 모델이 "카페" 같은 한글 값을 넣었다.
     */
    record ToolDefinition(String name, String description, JsonNode parameters) {

        public ToolDefinition {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("도구 이름이 필요합니다.");
            }
        }
    }

    /**
     * 도구 선택 방식. {@code AUTO}(모델이 정함), {@code REQUIRED}(반드시 도구 하나 이상), {@code NONE}(도구 금지),
     * 또는 특정 함수 강제({@code functionName}).
     */
    record ToolChoice(Mode mode, String functionName) {

        public enum Mode { AUTO, REQUIRED, NONE, FUNCTION }

        public static ToolChoice auto() {
            return new ToolChoice(Mode.AUTO, null);
        }

        public static ToolChoice required() {
            return new ToolChoice(Mode.REQUIRED, null);
        }

        public static ToolChoice none() {
            return new ToolChoice(Mode.NONE, null);
        }

        public static ToolChoice function(String name) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("강제할 함수 이름이 필요합니다.");
            }
            return new ToolChoice(Mode.FUNCTION, name);
        }
    }

    /**
     * 모델이 고른 도구 호출 1건. {@code argumentsJson}은 모델이 만든 JSON 문자열 그대로이며 검증·파싱은 도구 계층이 한다.
     */
    record ToolCall(String id, String name, String argumentsJson) {
    }

    /**
     * 한 턴의 응답.
     *
     * @param content      모델 문장. 도구만 골랐으면 빈 문자열
     * @param toolCalls    모델이 고른 도구 호출. 없으면 빈 목록
     * @param finishReason 공급자의 종료 사유({@code stop}·{@code tool_calls}·{@code length} 등). 없으면 null
     */
    record ChatTurn(String content, List<ToolCall> toolCalls, String finishReason) {

        public ChatTurn {
            content = content == null ? "" : content;
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }

        public boolean hasToolCalls() {
            return !toolCalls.isEmpty();
        }
    }
}
