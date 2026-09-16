package com.ssafy.dodam.domain.coach.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 프롬프트를 외부로 내보내기 직전의 마지막 검사. 민감정보 패턴이 하나라도 걸리면 호출하지 않는다.
 *
 * <p><b>왜 조립기에서 걸러 놓고 또 검사하는가</b>: {@code CoachFactAssembler}는 넘길 값을 명시적으로
 * 고르므로 정상 경로에서는 여기 걸릴 것이 없다. 문제는 진단·추천의 JSON 컬럼(`evidence`,
 * `currentValue`, `clusterContext`)이 스키마가 고정돼 있지 않다는 점이다 — 규칙이 추가되면서
 * 새 키가 들어오면 조립기의 키 필터를 통과해 버릴 수 있고, 그 사고는 "외부 API에 개인정보를
 * 보낸 뒤"에야 드러난다. 값 기반 검사를 한 겹 더 두어 되돌릴 수 없는 방향의 실수만 막는다.
 *
 * <p>검사는 의도적으로 좁다. 이름·주소처럼 형태가 없는 값은 정규식으로 잡을 수 없으므로 애초에
 * 조립기가 담지 않는 것으로 처리하고, 여기서는 <b>형태가 분명한 식별자</b>만 본다.
 *
 * <ul>
 *   <li>{@code EMAIL} — 이메일</li>
 *   <li>{@code LONG_DIGIT_RUN} — 10자리 이상 연속 숫자(계좌·카드번호). 금액은 천단위 구분 기호가
 *       들어간 문자열로 포맷되므로 여기 걸리지 않는다</li>
 *   <li>{@code HYPHENATED_IDENTIFIER} — 하이픈으로 끊긴 숫자 묶음 중 숫자 총합이 10자리 이상
 *       (계좌번호 {@code 110-123-456789}, 휴대폰 {@code 010-1234-5678}, 주민번호). 날짜
 *       {@code 2026-09-16}은 숫자가 8자리라 걸리지 않는다 — 이 경계가 규칙의 핵심이다</li>
 * </ul>
 */
@Component
public class CoachSensitiveDataGuard {

    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern LONG_DIGIT_RUN =
            Pattern.compile("(?<![0-9])[0-9]{10,}(?![0-9])");
    private static final Pattern HYPHENATED_DIGITS =
            Pattern.compile("(?<![0-9-])[0-9][0-9-]{7,}[0-9](?![0-9-])");
    private static final int IDENTIFIER_DIGIT_THRESHOLD = 10;

    /** @return 위반 종류 목록. 비어 있으면 내보내도 된다 */
    public List<String> scan(String payload) {
        List<String> violations = new ArrayList<>();
        if (payload == null || payload.isEmpty()) {
            return violations;
        }
        if (EMAIL.matcher(payload).find()) {
            violations.add("EMAIL");
        }
        if (LONG_DIGIT_RUN.matcher(payload).find()) {
            violations.add("LONG_DIGIT_RUN");
        }
        Matcher hyphenated = HYPHENATED_DIGITS.matcher(payload);
        while (hyphenated.find()) {
            if (digitCount(hyphenated.group()) >= IDENTIFIER_DIGIT_THRESHOLD) {
                violations.add("HYPHENATED_IDENTIFIER");
                break;
            }
        }
        return violations;
    }

    private static int digitCount(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                count++;
            }
        }
        return count;
    }
}
