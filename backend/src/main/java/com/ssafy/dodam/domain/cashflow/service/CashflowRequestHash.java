package com.ssafy.dodam.domain.cashflow.service;

import com.ssafy.dodam.domain.cashflow.dto.CashflowForecastPeriod;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 예측 요청 지문. 같은 지문이면 같은 계산이므로 캐시·멱등 판정의 기준이 된다.
 *
 * <p>입력은 사용자, 기준일, 기간, 계산 버전 넷이다. 계산 규칙이 바뀌어
 * {@link CashflowCalculationVersion#CURRENT}가 올라가면 이전 지문과 갈라져 기존 캐시가 자동으로
 * 무효화된다.
 *
 * <p>계좌번호·카드번호 같은 민감번호는 넣지 않는다. 지문은 응답과 로그에 남을 수 있다.
 */
public final class CashflowRequestHash {

    private CashflowRequestHash() {
    }

    public static String of(Long userId, CashflowForecastPeriod period, String calculationVersion) {
        // CashflowEventKeys와 같은 길이 접두 인코딩이다. 구분자만 쓰면 값 안의 '|'로 서로 다른 입력이
        // 같은 문자열이 될 수 있다.
        StringBuilder input = new StringBuilder("cashflow-request-v1");
        String[] parts = {
                userId.toString(),
                period.baseDate().toString(),
                Integer.toString(period.horizonDays()),
                calculationVersion
        };
        for (String part : parts) {
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
