package com.ssafy.dodam.domain.transaction.service;

import com.ssafy.dodam.domain.transaction.entity.Transaction;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import org.springframework.stereotype.Component;

/**
 * API 6 "메모 길이/금칙문자 검증".
 *
 * <ul>
 *   <li>앞뒤 공백을 제거하고, 비어 있으면 null(메모 삭제)로 본다.</li>
 *   <li>길이는 ERD `memo VARCHAR(500)` 기준 500자(코드포인트가 아니라 {@code String.length()})까지다.</li>
 *   <li>줄바꿈·탭을 제외한 제어문자(ISO 제어문자, 유니코드 포맷 문자)는 거절한다. 화면에서 보이지 않는 문자로
 *       메모를 채우거나 로그를 깨뜨리는 입력을 막기 위한 최소 규칙이다.</li>
 * </ul>
 */
@Component
public class TransactionMemoPolicy {

    public static final int MAX_LENGTH = Transaction.MEMO_MAX_LENGTH;

    public String normalize(String memo) {
        if (memo == null) {
            return null;
        }
        String trimmed = memo.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new InvalidRequestException("메모는 " + MAX_LENGTH + "자 이하여야 합니다.");
        }
        if (containsForbiddenCharacter(trimmed)) {
            throw new InvalidRequestException("메모에 사용할 수 없는 문자가 포함되어 있습니다.");
        }
        return trimmed;
    }

    private static boolean containsForbiddenCharacter(String value) {
        return value.codePoints().anyMatch(TransactionMemoPolicy::isForbidden);
    }

    private static boolean isForbidden(int codePoint) {
        if (codePoint == '\n' || codePoint == '\r' || codePoint == '\t') {
            return false;
        }
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.FORMAT
                || type == Character.PRIVATE_USE
                || type == Character.UNASSIGNED
                || type == Character.SURROGATE;
    }
}
