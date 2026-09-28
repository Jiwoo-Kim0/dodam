package com.ssafy.dodam.domain.simulation.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 복합 입력(POST /simulations/combined). 자식은 단독 유형의 확정 입력이며 평면 목록이다 — COMBINED 중첩·다른 자식 참조가
 * 구조적으로 불가능하므로 순환·재귀 실행이 생기지 않는다. 자식 간 중복·충돌 검증은 Resolver 단계의
 * {@code CombinedScenarioValidator}가 끝낸 뒤 여기로 온다.
 *
 * <p>입력 순서는 지문과 저장 sequenceNo에 포함된다. 순서를 바꾸면 별개 요청이지만 독립 효과의 합계는 같다.
 *
 * @param children 자식 입력 2~10개. 각 자식의 스냅샷(상품·대출)은 준비 단계에서 {@link #withChildren}으로 채운다
 */
public record CombinedInput(List<SimulationInput> children) implements SimulationInput {
    public static final int MIN_CHILDREN = 2;
    public static final int MAX_CHILDREN = 10;

    public CombinedInput {
        children = List.copyOf(children);
        if (children.size() < MIN_CHILDREN || children.size() > MAX_CHILDREN) {
            throw new IllegalArgumentException("scenarios는 " + MIN_CHILDREN + "~" + MAX_CHILDREN + "개여야 합니다.");
        }
        for (SimulationInput child : children) {
            if (child.type() == SimulationType.COMBINED) {
                throw new IllegalArgumentException("복합 시나리오는 중첩할 수 없습니다.");
            }
        }
    }

    public CombinedInput withChildren(List<SimulationInput> prepared) {
        return new CombinedInput(prepared);
    }

    @Override
    public SimulationType type() {
        return SimulationType.COMBINED;
    }

    /** 자식 중 가장 긴 표시 기간. 실제 표는 자식 종료 월 중 최댓값까지이며 120개월 상한이다. */
    @Override
    public int durationMonths() {
        return children.stream().mapToInt(SimulationInput::durationMonths).max().orElse(DEFAULT_DURATION_MONTHS);
    }

    /** 자식의 생략값(추가상환일 등)을 같은 기준일로 채운다. */
    @Override
    public SimulationInput withBaseDate(LocalDate baseDate) {
        List<SimulationInput> filled = new ArrayList<>(children.size());
        for (SimulationInput child : children) {
            filled.add(child.withBaseDate(baseDate));
        }
        return new CombinedInput(filled);
    }

    @Override
    public List<SimulationInput> scenarios() {
        return children;
    }

    /** 자식 유형·지문을 순서대로 길이 접두 인코딩한다. 자식 지문 안의 구분자로 다른 조합이 같은 문자열이 되지 않게 한다. */
    @Override
    public String fingerprint() {
        StringBuilder text = new StringBuilder();
        for (SimulationInput child : children) {
            String part = child.type().name() + ":" + child.fingerprint();
            text.append(part.length()).append(':').append(part).append(';');
        }
        return text.toString();
    }

    @Override
    public ObjectNode parameters(ObjectMapper json) {
        ObjectNode node = json.createObjectNode();
        ArrayNode scenarios = node.putArray("scenarios");
        for (SimulationInput child : children) {
            ObjectNode scenario = scenarios.addObject();
            scenario.put("type", child.type().name());
            scenario.set("parameters", child.parameters(json));
        }
        return node;
    }
}
