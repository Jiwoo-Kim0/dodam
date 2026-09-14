package com.ssafy.dodam.domain.recommendation.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * `recommendation.generation_key`(CHAR(64)) 생성.
 *
 * <p>같은 사용자·진단·목표·생성기 버전·후보 키에서 언제나 같은 값이 나와야 중복 생성 방지가 성립한다.
 * 시각·순번·UUID·표시 문구는 넣지 않는다. 새 진단은 diagnosisId가 다르므로 별도 키 공간이다.
 *
 * <p>입력은 구분자로 이어붙이지 않고 <b>길이 접두 직렬화</b>로 만든다. 단순 이어붙이기는
 * `("a", "bc")`와 `("ab", "c")`가 충돌한다. 형식은 `v1` 접두 뒤에 각 항목을 `길이:값`로 나열한다.
 * null은 값 `-`, 길이 `0`이 아닌 별도 표기 `n`으로 둬 빈 문자열과 구분한다.
 *
 * <p>SHA-256 hex(64자)는 CHAR(64) 컬럼과 정확히 맞고, 원본 항목이 키에서 역산되지 않는다.
 */
public final class RecommendationGenerationKey {

    // 키 구성 요소가 바뀌면 올린다. 옛 키와 충돌하지 않게 새 키 공간을 연다.
    static final String SCHEMA_VERSION = "v1";

    private RecommendationGenerationKey() {
    }

    /**
     * @param goalId 확정된 목표. null이면 목표 없는 추천으로 키에 그대로 반영된다
     */
    public static String of(Long userId, Long diagnosisId, Long goalId, String generatorVersion, String candidateKey) {
        if (userId == null || diagnosisId == null) {
            throw new IllegalArgumentException("userId와 diagnosisId는 필수입니다.");
        }
        if (isBlank(generatorVersion) || isBlank(candidateKey)) {
            throw new IllegalArgumentException("generatorVersion과 candidateKey는 비어 있을 수 없습니다.");
        }
        StringBuilder canonical = new StringBuilder(SCHEMA_VERSION);
        append(canonical, String.valueOf(userId));
        append(canonical, String.valueOf(diagnosisId));
        append(canonical, goalId == null ? null : String.valueOf(goalId));
        append(canonical, generatorVersion);
        append(canonical, candidateKey);
        return sha256Hex(canonical.toString());
    }

    private static void append(StringBuilder out, String value) {
        if (value == null) {
            out.append("|n");
            return;
        }
        out.append('|').append(value.length()).append(':').append(value);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
