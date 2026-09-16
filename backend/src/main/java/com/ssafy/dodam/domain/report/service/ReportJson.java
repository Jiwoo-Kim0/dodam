package com.ssafy.dodam.domain.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 리포트 JSON 컬럼과 응답 record 사이의 변환.
 *
 * <p>읽기는 실패를 흡수한다. 저장 형식이 바뀌어도 과거 리포트 조회가 500으로 깨지면 안 된다 —
 * 읽지 못한 부분은 null이 되고 {@code dataStatus}·{@code unavailableReason}이 그 사실을 알린다.
 */
@Component
public class ReportJson {

    private final ObjectMapper json;

    public ReportJson(ObjectMapper json) {
        this.json = json;
    }

    public JsonNode toJson(Object value) {
        return value == null ? null : json.valueToTree(value);
    }

    public <T> T fromJson(JsonNode node, Class<T> type) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return json.treeToValue(node, type);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException failure) {
            return null;
        }
    }
}
