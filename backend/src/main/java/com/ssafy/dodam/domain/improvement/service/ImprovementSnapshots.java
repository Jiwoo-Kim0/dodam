package com.ssafy.dodam.domain.improvement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.improvement.dto.ImprovementSnapshot;
import org.springframework.stereotype.Component;

/**
 * 스냅샷 record와 저장 JSON 사이의 변환. 읽기는 항상 실패를 흡수한다 — 과거 행의 형식이 바뀌어도 조회가
 * 깨지지 않아야 한다(진단 스냅샷과 같은 태도). 읽지 못한 스냅샷은 "기준선 없음"으로 취급된다.
 */
@Component
public class ImprovementSnapshots {

    private final ObjectMapper json;

    public ImprovementSnapshots(ObjectMapper json) {
        this.json = json;
    }

    public JsonNode toJson(ImprovementSnapshot snapshot) {
        return snapshot == null ? null : json.valueToTree(snapshot);
    }

    public ImprovementSnapshot fromJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return json.treeToValue(node, ImprovementSnapshot.class);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException failure) {
            return null;
        }
    }
}
