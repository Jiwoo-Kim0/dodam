package com.ssafy.dodam.domain.coach.service;

import com.ssafy.dodam.domain.coach.dto.CoachFacts;
import com.ssafy.dodam.domain.coach.dto.CoachFacts.Fact;
import com.ssafy.dodam.domain.coach.dto.CoachFacts.Section;
import com.ssafy.dodam.domain.coach.dto.CoachTopic;
import com.ssafy.dodam.domain.coach.dto.request.CoachChatRequest;
import com.ssafy.dodam.domain.coach.dto.response.CoachChatResponse;
import com.ssafy.dodam.domain.coach.dto.response.CoachDegradedReason;
import com.ssafy.dodam.domain.coach.dto.response.CoachMessageResponse;
import com.ssafy.dodam.domain.coach.dto.response.CoachSource;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator.ChatMessage;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator.ChatTurn;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator.ToolCall;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator.ToolChoice;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator.ToolDefinition;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 도리에게 묻기 — 대화형 시뮬레이션 에이전트(S15P21D105-640 A-3, 계획 §1-2).
 *
 * <p>한 턴의 흐름:
 * <ol>
 *   <li>세션 로드(사용자별·conversationId, 최근 N턴). 턴 상한이면 새 대화를 안내한다.</li>
 *   <li>사용자 문장에 {@link CoachSensitiveDataGuard} — 계좌·카드번호가 있으면 외부로 보내지 않는다.</li>
 *   <li>자유 입력: 1차 호출(messages + tools, {@code tool_choice=auto}). tool_calls가 없으면 그 답이 최종.
 *       칩({@code chipId}): 1차 호출을 건너뛰고 서버 고정 매핑으로 도구를 바로 실행한다.</li>
 *   <li>{@link CoachToolDispatcher}가 도구를 실행한다(최대 3개). 결과는 서버 포맷 문자열({@link CoachFacts})이다.</li>
 *   <li>2차 호출({@code tool_choice=none}) → 문장. 또 tool_calls가 오면 {@code TOOL_LOOP_EXCEEDED}.</li>
 *   <li>{@link CoachNumberGuard} — 허용 숫자 = facts + 사용자 문장 + 화면 맥락 + 앞선 턴의 facts.</li>
 * </ol>
 * 어느 단계에서 막혀도 HTTP 200 + {@code source=TEMPLATE}이며, 이미 실행한 도구의 facts는 그대로 내려간다 —
 * 시뮬레이션은 서버가 계산했으므로 문장이 없어도 결과 카드는 그릴 수 있다.
 *
 * <p>3종 코치({@link CoachService})와 다른 점: 응답을 <b>캐시하지 않는다</b>(같은 문장도 대화 맥락이 다르다),
 * 예산은 별도 키({@link CoachChatRateLimiter})로 호출 1회마다 센다, 세션에 사용자 문장과 facts만 남긴다.
 */
@Service
public class CoachChatService {

    private static final Logger log = LoggerFactory.getLogger(CoachChatService.class);

    static final String SUBJECT_DEFAULT = "도리에게 묻기";
    static final String MESSAGE_REQUIRED = "질문 또는 chipId가 필요합니다.";
    public static final String SENSITIVE_MESSAGE =
            "계좌번호·카드번호처럼 긴 숫자는 채팅에 적지 말아 주세요. 번호를 빼고 다시 물어봐 주시면 도와드릴게요.";
    public static final String TURN_LIMIT_MESSAGE = "이 대화에서 나눌 수 있는 질문 수를 다 썼어요. 새 대화를 시작해 주세요.";
    /** 앞선 턴을 재생할 때 assistant 자리에 넣는 머리말. 모델 응답 전문은 저장하지 않으므로 facts 요약으로 대신한다. */
    public static final String HISTORY_ASSISTANT_PREFIX = "(앞선 답변의 서버 계산값) ";
    static final String HISTORY_ASSISTANT_EMPTY = "(앞선 답변은 계산 없이 안내만 했다)";

    private final CoachTextGenerator generator;
    private final CoachToolRegistry registry;
    private final CoachToolDispatcher dispatcher;
    private final CoachPromptFactory prompts;
    private final CoachTemplateWriter template;
    private final CoachNumberGuard numbers;
    private final CoachSensitiveDataGuard sensitive;
    private final CoachChatRateLimiter rateLimiter;
    private final CoachChatSessionStore sessions;
    private final CoachFactAssembler assembler;
    private final CoachChatProperties properties;
    private final CoachClock clock;

    public CoachChatService(CoachTextGenerator generator, CoachToolRegistry registry, CoachToolDispatcher dispatcher,
            CoachPromptFactory prompts, CoachTemplateWriter template, CoachNumberGuard numbers,
            CoachSensitiveDataGuard sensitive, CoachChatRateLimiter rateLimiter, CoachChatSessionStore sessions,
            CoachFactAssembler assembler, CoachChatProperties properties, CoachClock clock) {
        this.generator = generator;
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.prompts = prompts;
        this.template = template;
        this.numbers = numbers;
        this.sensitive = sensitive;
        this.rateLimiter = rateLimiter;
        this.sessions = sessions;
        this.assembler = assembler;
        this.properties = properties;
        this.clock = clock;
    }

    public CoachChatResponse chat(Long userId, CoachChatRequest request) {
        String chipId = blankToNull(request.chipId());
        String message = blankToNull(request.message());
        if (chipId == null && message == null) {
            throw new InvalidRequestException(MESSAGE_REQUIRED);
        }
        if (message != null && message.length() > properties.maxMessageLength()) {
            throw new InvalidRequestException("질문은 " + properties.maxMessageLength() + "자 이내로 적어 주세요.");
        }
        List<ToolCall> chipCalls = null;
        if (chipId != null) {
            chipCalls = registry.chip(chipId)
                    .orElseThrow(() -> new InvalidRequestException("알 수 없는 chipId입니다: " + chipId));
        }
        // 칩이면 세션·프롬프트에는 칩 표시 문구를 사용자 문장으로 쓴다(서버 문자열이라 민감정보 검사가 필요 없다)
        String userText = chipId != null ? CoachToolRegistry.chipLabel(chipId).orElse(message == null ? chipId : message) : message;

        CoachChatSession session = load(userId, request.conversationId());
        if (session.exhausted(properties.maxTurnsPerSession())) {
            log.info("Coach chat refused by the session turn cap: userId={} turns={}", userId, session.turnCount());
            return respond(session, CoachSource.TEMPLATE, CoachDegradedReason.SESSION_TURN_LIMIT, null,
                    TURN_LIMIT_MESSAGE, emptyFacts(), List.of(), chipId);
        }
        if (chipId == null) {
            List<String> violations = sensitive.scan(userText);
            if (!violations.isEmpty()) {
                // 사용자가 적은 번호다. 외부로 보내지 않고, 문장도 남기지 않는다(세션 저장 없음)
                log.info("Coach chat message blocked before the external call: userId={} violations={}", userId, violations);
                return respond(session, CoachSource.TEMPLATE, CoachDegradedReason.SENSITIVE_DATA_BLOCKED, null,
                        SENSITIVE_MESSAGE, emptyFacts(), List.of(), chipId);
            }
        }
        CoachFacts context = contextFacts(userId, request.context());
        return run(userId, session, userText, chipCalls, context, chipId);
    }

    // ---------- 턴 실행 ----------

    private CoachChatResponse run(Long userId, CoachChatSession session, String userText, List<ToolCall> chipCalls,
            CoachFacts context, String chipId) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(prompts.chatSystem(context)));
        replay(messages, session);
        messages.add(ChatMessage.user(userText));
        List<ToolDefinition> tools = registry.definitions();
        List<String> extraNumberSources = extraNumberSources(userText, context, session);

        List<ToolCall> calls;
        String directAnswer = null;
        if (chipCalls != null) {
            calls = chipCalls;
        } else {
            if (!generator.isAvailable()) {
                return finish(userId, session, userText, chipId, CoachDegradedReason.AI_DISABLED, emptyFacts(), List.of(), null);
            }
            if (!rateLimiter.tryAcquire(userId)) {
                log.warn("Coach chat call refused by the per-user budget: userId={}", userId);
                return finish(userId, session, userText, chipId, CoachDegradedReason.RATE_LIMITED, emptyFacts(), List.of(), null);
            }
            ChatTurn first;
            try {
                first = generator.chat(messages, tools, ToolChoice.auto());
            } catch (CoachGenerationException exception) {
                log.warn("Coach chat first call failed, falling back to template: reason={}", exception.getMessage());
                return finish(userId, session, userText, chipId, CoachDegradedReason.AI_UNAVAILABLE, emptyFacts(), List.of(), null);
            }
            if (first.hasToolCalls()) {
                calls = first.toolCalls();
            } else {
                calls = List.of();
                directAnswer = first.content();
            }
        }

        List<CoachToolResult> results = calls.isEmpty() ? List.of() : dispatcher.dispatch(userId, calls);
        CoachFacts facts = facts(results);

        String answer;
        if (directAnswer != null) {
            answer = directAnswer;
        } else {
            if (!generator.isAvailable()) {
                return finish(userId, session, userText, chipId, CoachDegradedReason.AI_DISABLED, facts, results, null);
            }
            if (!rateLimiter.tryAcquire(userId)) {
                log.warn("Coach chat second call refused by the per-user budget: userId={}", userId);
                return finish(userId, session, userText, chipId, CoachDegradedReason.RATE_LIMITED, facts, results, null);
            }
            messages.add(ChatMessage.assistant(null, calls));
            results.forEach(result -> messages.add(ChatMessage.tool(result.toolCallId(), result.toModelText())));
            ChatTurn second;
            try {
                second = generator.chat(messages, tools, ToolChoice.none());
            } catch (CoachGenerationException exception) {
                log.warn("Coach chat second call failed, falling back to template: reason={}", exception.getMessage());
                return finish(userId, session, userText, chipId, CoachDegradedReason.AI_UNAVAILABLE, facts, results, null);
            }
            if (second.hasToolCalls()) {
                // 왕복은 1회다. 도구를 더 부르려 하면 문장 없이 끝난 것이므로 템플릿이 facts로 말한다
                log.warn("Coach chat model asked for tools again after the tool round: tools={}",
                        second.toolCalls().stream().map(ToolCall::name).toList());
                return finish(userId, session, userText, chipId, CoachDegradedReason.TOOL_LOOP_EXCEEDED, facts, results, null);
            }
            answer = second.content();
        }

        if (answer == null || answer.isBlank()) {
            log.warn("Coach chat returned an empty body: tools={}", results.size());
            return finish(userId, session, userText, chipId, CoachDegradedReason.AI_EMPTY_RESPONSE, facts, results, null);
        }
        Optional<String> unverified = numbers.findUnverifiedNumber(answer, facts, extraNumberSources);
        if (unverified.isPresent()) {
            log.warn("Coach chat answer contains a number that was not provided: token={}", unverified.get());
            return finish(userId, session, userText, chipId, CoachDegradedReason.AI_UNVERIFIED_NUMBER, facts, results, null);
        }
        String trimmed = answer.trim();
        int limit = prompts.maxMessageLength(CoachTopic.CHAT);
        if (trimmed.length() > limit) {
            log.warn("Coach chat answer is longer than the prompt asked: length={} limit={}", trimmed.length(), limit);
        }
        return finish(userId, session, userText, chipId, null, facts, results, trimmed);
    }

    /** 턴을 세션에 남기고 응답을 만든다. {@code aiAnswer}가 null이면 템플릿 경로다. */
    private CoachChatResponse finish(Long userId, CoachChatSession session, String userText, String chipId,
            CoachDegradedReason reason, CoachFacts facts, List<CoachToolResult> results, String aiAnswer) {
        CoachChatSession next = session.append(new CoachChatSession.Turn(userText, factLines(facts)), properties.historyTurns());
        sessions.save(userId, next);
        if (aiAnswer != null) {
            return respond(next, CoachSource.AI, null, generator.modelName(), aiAnswer, facts, results, chipId);
        }
        return respond(next, CoachSource.TEMPLATE, reason, null, template.write(facts), facts, results, chipId);
    }

    private CoachChatResponse respond(CoachChatSession session, CoachSource source, CoachDegradedReason reason, String model,
            String message, CoachFacts facts, List<CoachToolResult> results, String chipId) {
        List<CoachChatResponse.ToolResult> toolResults = results.stream()
                .map(result -> new CoachChatResponse.ToolResult(result.tool(), result.simulationId(), result.created(), result.error()))
                .toList();
        return new CoachChatResponse(CoachTopic.CHAT, source, reason, model, clock.now(), session.conversationId(), message,
                facts, toolResults, suggestedQuestions(chipId), CoachMessageResponse.DISCLAIMER);
    }

    // ---------- 세션 ----------

    private CoachChatSession load(Long userId, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return CoachChatSession.start(CoachChatSession.newId());
        }
        // 형식은 요청 검증이 봤다. 만료됐거나 저장소가 없으면 같은 id로 새 대화를 시작한다 — FE는 id를 바꿀 필요가 없다
        return sessions.find(userId, conversationId).orElseGet(() -> CoachChatSession.start(conversationId));
    }

    /** 앞선 턴을 user/assistant 쌍으로 재생한다. assistant 자리는 저장해 둔 facts 요약이다(응답 전문은 저장하지 않는다). */
    private static void replay(List<ChatMessage> messages, CoachChatSession session) {
        for (CoachChatSession.Turn turn : session.turns()) {
            messages.add(ChatMessage.user(turn.userMessage()));
            messages.add(ChatMessage.assistant(turn.facts().isEmpty() ? HISTORY_ASSISTANT_EMPTY
                    : HISTORY_ASSISTANT_PREFIX + String.join(" · ", turn.facts()), List.of()));
        }
    }

    private static List<String> factLines(CoachFacts facts) {
        List<String> lines = new ArrayList<>();
        for (Section section : facts.sections()) {
            for (Fact fact : section.facts()) {
                if (lines.size() >= CoachChatSession.FACT_LINES_PER_TURN) {
                    return lines;
                }
                lines.add(fact.label() + ": " + fact.value());
            }
        }
        return lines;
    }

    // ---------- facts ----------

    private CoachFacts contextFacts(Long userId, CoachChatRequest.Context context) {
        if (context == null) {
            return null;
        }
        if (!context.isComplete()) {
            throw new InvalidRequestException("context에는 diagnosisId와 issueId가 모두 필요합니다.");
        }
        // 본인 것이 아니면 여기서 404다(3종 코치와 같은 조립기·같은 규칙)
        return assembler.inefficiency(userId, context.diagnosisId(), context.issueId());
    }

    /** 도구 결과를 하나의 facts로 합친다. 실패한 도구는 별도 섹션에 사용자용 도구 이름과 사유로 남긴다. */
    static CoachFacts facts(List<CoachToolResult> results) {
        List<Section> sections = new ArrayList<>();
        LinkedHashSet<String> notes = new LinkedHashSet<>();
        List<Fact> failures = new ArrayList<>();
        String subject = null;
        for (CoachToolResult result : results) {
            if (result.succeeded()) {
                sections.addAll(result.sections());
                notes.addAll(result.notes());
                if (subject == null) {
                    subject = CoachToolRegistry.displayName(result.tool());
                }
            } else {
                failures.add(new Fact(CoachToolRegistry.displayName(result.tool()), result.error()));
            }
        }
        if (!failures.isEmpty()) {
            sections.add(new Section(CoachTemplateWriter.FAILED_SECTION_TITLE, failures));
        }
        return new CoachFacts(CoachTopic.CHAT, subject == null ? SUBJECT_DEFAULT : subject, sections, List.copyOf(notes));
    }

    private static CoachFacts emptyFacts() {
        return new CoachFacts(CoachTopic.CHAT, SUBJECT_DEFAULT, List.of(), List.of());
    }

    /** 숫자 검증의 추가 허용 출처: 사용자 문장, 화면 맥락 facts, 앞선 턴의 사용자 문장·facts. */
    private static List<String> extraNumberSources(String userText, CoachFacts context, CoachChatSession session) {
        List<String> sources = new ArrayList<>();
        sources.add(userText);
        if (context != null) {
            sources.add(context.subject());
            sources.addAll(context.notes());
            context.sections().forEach(section -> section.facts().forEach(fact -> {
                sources.add(fact.label());
                sources.add(fact.value());
            }));
        }
        for (CoachChatSession.Turn turn : session.turns()) {
            sources.add(turn.userMessage());
            sources.addAll(turn.facts());
        }
        return sources;
    }

    private static List<String> suggestedQuestions(String chipId) {
        String used = chipId == null ? null : CoachToolRegistry.chipLabel(chipId).orElse(null);
        return CoachToolRegistry.chipLabels().stream().filter(label -> !label.equals(used)).toList();
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
