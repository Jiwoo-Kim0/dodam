package com.ssafy.dodam.domain.improvement.dto;

import java.time.LocalDateTime;

/**
 * 재진단 요청(API 19 #11).
 *
 * <p>신규 진단이면 <b>201 + {@code created: true}</b>, 중복 요청이 막혀 직전 진단을 돌려주면
 * <b>200 + {@code created: false}</b>다. 진행 중이면 409 {@code DIAGNOSIS_IN_PROGRESS}.
 * 진단 실행 API(`POST /api/v1/diagnoses`)가 이미 쓰는 규약과 같으며 중복 방지 판정도 그쪽 정책
 * ({@code DiagnosisExecutionStore})을 그대로 따른다 — 같은 날 같은 규칙 버전의 완료 진단이 있으면 재사용한다.
 */
public record RediagnoseResultResponse(
        Long diagnosisId,
        Long goalId,
        boolean created,
        LocalDateTime requestedAt
) {
}
