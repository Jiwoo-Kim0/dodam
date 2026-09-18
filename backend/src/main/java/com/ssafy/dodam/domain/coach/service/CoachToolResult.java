package com.ssafy.dodam.domain.coach.service;

import com.ssafy.dodam.domain.coach.dto.CoachFacts.Fact;
import com.ssafy.dodam.domain.coach.dto.CoachFacts.Section;
import java.util.List;

/**
 * 도구 실행 결과 1건(640 A-2). 서버가 계산한 값을 이미 포맷된 문자열({@link Section})로 담는다 — 모델은 이 문자열만 본다.
 * 성공이면 {@code sections}, 실패면 {@code error}(사용자에게 보여도 되는 문장)다. 두 경우 모두 2차 호출의 {@code tool} 메시지로 돌아간다.
 *
 * @param toolCallId   모델(또는 칩)이 만든 호출 id. tool 메시지의 {@code tool_call_id}
 * @param tool         도구 이름
 * @param simulationId 시뮬레이션 도구가 만든(또는 재사용한) 행. FE "자세히" 링크. 조회 도구·실패면 null
 * @param created      시뮬레이션을 새로 계산했는지(같은 날 같은 요청이면 false)
 * @param sections     서버 계산 사실. 실패면 비어 있다
 * @param notes        모델이 지키게 할 서술(단정 금지 등)
 * @param error        실패 사유 문장. 성공이면 null
 */
public record CoachToolResult(String toolCallId, String tool, Long simulationId, Boolean created, List<Section> sections,
        List<String> notes, String error) {

    public CoachToolResult {
        sections = sections == null ? List.of() : List.copyOf(sections);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    public static CoachToolResult ok(String toolCallId, String tool, Long simulationId, Boolean created, List<Section> sections,
            List<String> notes) {
        return new CoachToolResult(toolCallId, tool, simulationId, created, sections, notes, null);
    }

    public static CoachToolResult error(String toolCallId, String tool, String message) {
        return new CoachToolResult(toolCallId, tool, null, null, List.of(), List.of(), message);
    }

    public boolean succeeded() {
        return error == null;
    }

    /**
     * 2차 호출에 넘기는 tool 메시지 본문. 자유 JSON을 넘기지 않고 "라벨: 값" 줄만 넘긴다(계획 §1-6 "정해진 필드만 옮긴다").
     */
    public String toModelText() {
        if (!succeeded()) {
            return "실행 실패: " + error;
        }
        StringBuilder text = new StringBuilder();
        for (Section section : sections) {
            text.append('[').append(section.title()).append("]\n");
            for (Fact fact : section.facts()) {
                text.append("- ").append(fact.label()).append(": ").append(fact.value()).append('\n');
            }
        }
        for (String note : notes) {
            text.append("※ ").append(note).append('\n');
        }
        return text.toString().strip();
    }
}
