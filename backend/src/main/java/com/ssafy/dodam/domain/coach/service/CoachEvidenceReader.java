package com.ssafy.dodam.domain.coach.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.coach.dto.CoachFacts;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 진단·추천의 스키마 미고정 JSON 컬럼(`currentValue`, `benchmarkValue`, `evidence`, `clusterContext`)에서
 * 프롬프트에 실을 값만 뽑는다.
 *
 * <p><b>왜 통째로 넘기지 않는가</b>: 이 컬럼들은 의도적으로 스키마를 고정하지 않았다(엔티티 주석 참고).
 * 진단 규칙이 늘면 키도 는다. 통째로 직렬화하면 규칙 하나 추가할 때마다 외부 API로 나가는 내용이
 * 코드 변경 없이 넓어지고, 그 안에 `targetId`·`forecastId` 같은 내부 식별자가 이미 들어 있다
 * ({@code DiagnosisEvaluator}가 넣는다). 설명 문장에 쓸모도 없는 값들이다.
 *
 * <p>걸러내는 기준:
 * <ul>
 *   <li><b>키 이름</b> — {@code ...Id}/{@code ...No}/{@code ...Number}로 끝나거나 이름에 이메일·전화·주소·
 *       계좌·이름류 단어가 들어가면 버린다. 식별자는 설명에 쓰이지 않으므로 잃을 것이 없다</li>
 *   <li><b>자릿수</b> — 정수 자릿수가 10자리를 넘는 값은 버린다. 개인 재무 금액으로는 비현실적인 크기고
 *       (10자리 = 10억 원), 계좌·카드번호가 숫자로 들어왔을 때의 마지막 방어선이다.
 *       {@link CoachSensitiveDataGuard}는 천단위 구분 기호가 붙은 뒤의 문자열을 보므로 이 경우를 놓친다</li>
 *   <li><b>길이·개수</b> — 긴 문자열과 깊은 중첩은 자른다. 프롬프트 길이는 곧 비용이고, 길수록 모델이
 *       지시를 놓친다</li>
 * </ul>
 */
@Component
public class CoachEvidenceReader {

    static final int MAX_FACTS = 12;
    static final int MAX_DEPTH = 2;
    static final int MAX_ARRAY_ELEMENTS = 3;
    static final int MAX_TEXT_LENGTH = 120;
    /** 이 자릿수를 넘는 정수는 금액이 아니라 식별자로 본다. */
    static final int MAX_INTEGER_DIGITS = 10;

    private static final Pattern DENIED_KEY = Pattern.compile(
            "(?i).*(id|no|num|number)$"
                    + "|(?i).*(email|phone|mobile|tel|address|account|birth|ssn|rrn|holder|owner|nickname|username|name).*");

    /**
     * 이름 규칙보다 우선하는 예외. {@code ...name}은 사람 이름이 들어올 수 있어 막지만, 카테고리·상품·기관
     * 이름은 설명의 핵심이라("외식 카테고리가 기준보다 높다") 막으면 문장이 무의미해진다. 개인정보가
     * 아닌 것이 확실한 키만 이름으로 지정해 되살린다.
     */
    private static final Set<String> ALLOWED_KEYS = Set.of(
            "categoryName", "productName", "bankName", "companyName", "clusterName", "ruleName", "displayName");

    /**
     * @param prefix 키 경로 앞에 붙일 접두어. 비면 키 이름만 라벨이 된다
     * @return 라벨-값 쌍 목록. 넣을 것이 없으면 빈 목록
     */
    public List<CoachFacts.Fact> read(String prefix, JsonNode node) {
        List<CoachFacts.Fact> facts = new ArrayList<>();
        collect(facts, prefix, node, 0);
        return facts;
    }

    private void collect(List<CoachFacts.Fact> facts, String label, JsonNode node, int depth) {
        if (node == null || node.isNull() || node.isMissingNode() || facts.size() >= MAX_FACTS) {
            return;
        }
        if (node.isObject()) {
            if (depth >= MAX_DEPTH) {
                return;
            }
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                if (facts.size() >= MAX_FACTS) {
                    break;
                }
                if (isDenied(field.getKey())) {
                    continue;
                }
                collect(facts, join(label, field.getKey()), field.getValue(), depth + 1);
            }
            return;
        }
        if (node.isArray()) {
            if (depth >= MAX_DEPTH) {
                return;
            }
            int limit = Math.min(node.size(), MAX_ARRAY_ELEMENTS);
            for (int i = 0; i < limit && facts.size() < MAX_FACTS; i++) {
                collect(facts, label + "[" + (i + 1) + "]", node.get(i), depth + 1);
            }
            return;
        }
        String value = scalar(node);
        if (value != null) {
            facts.add(new CoachFacts.Fact(label, value));
        }
    }

    private static boolean isDenied(String key) {
        return !ALLOWED_KEYS.contains(key) && DENIED_KEY.matcher(key).matches();
    }

    private static String scalar(JsonNode node) {
        if (node.isBoolean()) {
            return node.booleanValue() ? "예" : "아니오";
        }
        if (node.isNumber()) {
            BigDecimal value = node.decimalValue();
            if (value.precision() - value.scale() > MAX_INTEGER_DIGITS) {
                return null;
            }
            return CoachFormat.number(value);
        }
        if (node.isTextual()) {
            String text = node.textValue().trim();
            if (text.isEmpty() || text.length() > MAX_TEXT_LENGTH) {
                return null;
            }
            return text;
        }
        return null;
    }

    private static String join(String prefix, String key) {
        return prefix == null || prefix.isBlank() ? key : prefix + "." + key;
    }
}
