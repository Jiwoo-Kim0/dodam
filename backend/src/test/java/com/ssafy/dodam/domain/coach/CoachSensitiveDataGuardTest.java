package com.ssafy.dodam.domain.coach;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.dodam.domain.coach.service.CoachSensitiveDataGuard;
import org.junit.jupiter.api.Test;

/** 프롬프트를 외부로 내보내기 직전의 민감정보 검사. 정상 값이 걸리지 않는 것이 절반의 요구사항이다. */
class CoachSensitiveDataGuardTest {

    private final CoachSensitiveDataGuard guard = new CoachSensitiveDataGuard();

    @Test
    void blocksIdentifiers() {
        assertThat(guard.scan("문의: dodam@example.com")).contains("EMAIL");
        assertThat(guard.scan("계좌 0011223344556")).contains("LONG_DIGIT_RUN");
        assertThat(guard.scan("계좌 110-123-456789")).contains("HYPHENATED_IDENTIFIER");
        assertThat(guard.scan("연락처 010-1234-5678")).contains("HYPHENATED_IDENTIFIER");
    }

    @Test
    void passesNormalCoachFacts() {
        // 금액은 천단위 구분 기호가 붙어 내려오므로 긴 숫자열로 보이지 않는다
        assertThat(guard.scan("- 월 절감액: 1,250,000원")).isEmpty();
        // 날짜는 숫자가 8자리라 계좌번호 규칙에 걸리지 않는다 — 이 경계가 규칙의 핵심이다
        assertThat(guard.scan("- 진단 완료 시각: 2026-09-16 12:00")).isEmpty();
        assertThat(guard.scan("- 기간: 2026-09-16 ~ 2026-10-16")).isEmpty();
        assertThat(guard.scan("- 분류 신뢰도: 62.5%")).isEmpty();
        assertThat(guard.scan(null)).isEmpty();
    }
}
