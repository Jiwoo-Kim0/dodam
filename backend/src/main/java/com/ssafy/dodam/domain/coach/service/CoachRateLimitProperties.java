package com.ssafy.dodam.domain.coach.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 코치 호출 예산 정책값({@code app.coach.rate-limit}).
 *
 * <p>기본값 <b>시간당 40건</b>의 근거: 한 화면을 처음 여는 데 필요한 생성 호출은 요약 1 + 문제 설명
 * 최대 6 + 추천 설명 5로 12건 남짓이고, 같은 사실을 다시 부르면 캐시 적중이라 예산을 쓰지 않는다.
 * 즉 40건은 "한 시간에 재진단을 세 번 하고 모든 항목을 새로 펼쳐 보는" 사용보다도 넉넉하다. 반대로
 * 폭주하는 코드를 사용자당 시간당 40건으로 묶어 비용 상한을 만든다.
 *
 * @param enabled 끄면 항상 통과한다. 테스트·로컬 기본 off
 * @param permits 한 창(window) 안에서 허용할 생성 호출 수
 * @param window  고정 창 길이. epoch 기준으로 정렬되므로 창 경계에서는 최악 2배까지 몰릴 수 있다
 *                (슬라이딩 윈도우 대신 고정 창을 고른 이유는 backend/docs/ai-coach.md 12절)
 */
@ConfigurationProperties(prefix = "app.coach.rate-limit")
public record CoachRateLimitProperties(Boolean enabled, Integer permits, Duration window) {

    public CoachRateLimitProperties {
        enabled = enabled == null || enabled;
        permits = permits == null || permits <= 0 ? 40 : permits;
        window = window == null || window.isZero() || window.isNegative() ? Duration.ofHours(1) : window;
    }
}
