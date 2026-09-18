package com.ssafy.dodam.domain.coach.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 모델이 만든 도구 인자 JSON 문자열을 읽고 검증한다(640 A-2). 인자는 신뢰하지 않는다 — 스키마를 보냈어도 모델은 형식을 어길 수
 * 있으므로 여기서 형식·범위를 한 번 더 막는다. 사용자 식별자·계좌번호 원문은 스키마에 없고 여기서도 읽지 않는다.
 */
public final class CoachToolArguments {

    /** 채팅으로 한 번에 다룰 금액 상한(1억 원). 그 이상은 입력 오류로 본다. */
    public static final long MAX_AMOUNT = 100_000_000L;

    private final JsonNode node;

    private CoachToolArguments(JsonNode node) {
        this.node = node;
    }

    public static CoachToolArguments parse(ObjectMapper json, String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return new CoachToolArguments(json.createObjectNode());
        }
        try {
            JsonNode parsed = json.readTree(argumentsJson);
            if (!parsed.isObject()) {
                throw new CoachToolArgumentException("도구 인자를 읽을 수 없어요. 다시 물어봐 주세요.");
            }
            return new CoachToolArguments(parsed);
        } catch (IOException exception) {
            throw new CoachToolArgumentException("도구 인자를 읽을 수 없어요. 다시 물어봐 주세요.");
        }
    }

    public static CoachToolArguments of(JsonNode node) {
        return new CoachToolArguments(node == null ? new ObjectMapper().createObjectNode() : node);
    }

    public boolean has(String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() && !(value.isTextual() && value.asText().isBlank());
    }

    /** 원 단위 금액. 정수·정수형 문자열·소수(정수부만) 허용, 0 이하나 상한 초과는 오류. 없으면 null. */
    public Long amount(String field, String label) {
        if (!has(field)) {
            return null;
        }
        JsonNode value = node.get(field);
        long parsed;
        if (value.isNumber()) {
            parsed = value.longValue();
        } else {
            String text = value.asText().replace(",", "").replace("원", "").strip();
            try {
                parsed = (long) Double.parseDouble(text);
            } catch (NumberFormatException exception) {
                throw new CoachToolArgumentException(label + "은(는) 숫자(원)로 알려 주세요.");
            }
        }
        if (parsed <= 0) {
            throw new CoachToolArgumentException(label + "은(는) 0보다 큰 금액이어야 해요.");
        }
        if (parsed > MAX_AMOUNT) {
            throw new CoachToolArgumentException(label + "이(가) 너무 커요. 1억 원 이하로 알려 주세요.");
        }
        return parsed;
    }

    public long requiredAmount(String field, String label) {
        Long value = amount(field, label);
        if (value == null) {
            throw new CoachToolArgumentException(label + "을(를) 알려 주세요.");
        }
        return value;
    }

    /** 1~120 개월. 없으면 null(시뮬레이션 기본값). */
    public Integer months(String field) {
        if (!has(field)) {
            return null;
        }
        JsonNode value = node.get(field);
        int parsed;
        try {
            parsed = value.isNumber() ? value.intValue() : Integer.parseInt(value.asText().strip());
        } catch (NumberFormatException exception) {
            throw new CoachToolArgumentException("기간은 개월 수(1~120)로 알려 주세요.");
        }
        if (parsed < 1 || parsed > 120) {
            throw new CoachToolArgumentException("기간은 1~120개월 사이여야 해요.");
        }
        return parsed;
    }

    public String text(String field) {
        return has(field) ? node.get(field).asText().strip() : null;
    }

    /** 대문자 코드. 허용 집합 밖이면 오류(모델이 한글 이름을 넣은 경우 등). 없으면 null. */
    public String code(String field, Set<String> allowed, String label) {
        String value = text(field);
        if (value == null) {
            return null;
        }
        String code = value.toUpperCase(Locale.ROOT).replace(' ', '_');
        if (!allowed.contains(code)) {
            throw new CoachToolArgumentException(label + " '" + value + "'은(는) 알 수 없는 값이에요. 가능한 값: " + String.join(", ", allowed));
        }
        return code;
    }

    /** 배열 필드의 원소들. 없으면 빈 목록. */
    public List<CoachToolArguments> items(String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        List<CoachToolArguments> items = new ArrayList<>();
        for (JsonNode item : value) {
            if (item.isObject()) {
                items.add(new CoachToolArguments(item));
            }
        }
        return items;
    }

    public ObjectNode raw() {
        return node.isObject() ? (ObjectNode) node : new ObjectMapper().createObjectNode();
    }
}
