package com.ssafy.dodam.domain.cashflow.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class CashflowEventKeys {
    private CashflowEventKeys() { }

    /** 금액·표시명은 제외한다. 같은 회차의 금액 수정이 새로운 출금으로 더해지면 안 된다. */
    public static String key(Long userId, String sourceType, String sourceId, String occurrence) {
        StringBuilder input = new StringBuilder("cashflow-event-v1");
        for (String part : new String[] {userId.toString(), sourceType, sourceId, occurrence}) {
            input.append('|').append(part.length()).append(':').append(part);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
