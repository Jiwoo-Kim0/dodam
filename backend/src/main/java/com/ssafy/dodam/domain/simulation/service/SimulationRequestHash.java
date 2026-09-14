package com.ssafy.dodam.domain.simulation.service;

import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

/**
 * 요청 지문. 같은 사용자·유형·입력·기준일·계산 버전이면 같은 계산이므로 같은 날 다시 만들지 않는 근거가 된다.
 *
 * <p>CashflowRequestHash와 같은 길이 접두 인코딩이다. 구분자만 쓰면 값 안의 '|'로 다른 입력이 같은 문자열이 될 수 있다.
 * 계좌번호 같은 민감번호는 입력에 넣지 않는다(SimulationInput.fingerprint는 내부 id를 쓴다). 지문은 응답에서 제거하지만
 * 로그에 남을 수 있다는 전제로 다룬다.
 */
public final class SimulationRequestHash {
    private static final String SCHEMA = "simulation-request-v1";

    private SimulationRequestHash() {
    }

    public static String of(Long userId, SimulationType type, String fingerprint, LocalDate baseDate, String calculationVersion) {
        StringBuilder input = new StringBuilder(SCHEMA);
        for (String part : new String[] {userId.toString(), type.name(), fingerprint, baseDate.toString(), calculationVersion}) {
            input.append('|').append(part.length()).append(':').append(part);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
