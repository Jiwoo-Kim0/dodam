package com.ssafy.dodam.domain.notification.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 알림 푸시·이벤트 정책값(`app.notification`).
 *
 * @param push   FCM 푸시. `enabled=false`면 발송하지 않고 로그만 남긴다(로컬·테스트). 서비스 계정 키 경로는 compose가 `/run/secrets/...`로 마운트한다
 * @param events 이벤트 연동(S15P21D105-581). 결제·상환 예정은 오늘부터 `dueDaysAhead`일 안, 현금흐름 위험은 `riskDaysAhead`일 안,
 *               추천은 최근 `recommendationDaysBack`일 안에 생성된 것을 알림으로 만든다. 스케줄 cron은 서비스 시간대(Asia/Seoul) 기준
 */
@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(Push push, Events events) {

    public NotificationProperties {
        push = push == null ? new Push(null, null) : push;
        events = events == null ? new Events(null, null, null, null) : events;
    }

    public record Push(Boolean enabled, String serviceAccountPath) {
        public Push {
            enabled = enabled != null && enabled;
            serviceAccountPath = serviceAccountPath == null ? "" : serviceAccountPath.strip();
        }
    }

    public record Events(Boolean enabled, Integer dueDaysAhead, Integer riskDaysAhead, Integer recommendationDaysBack) {
        public Events {
            enabled = enabled == null || enabled;
            dueDaysAhead = dueDaysAhead == null ? 3 : dueDaysAhead;
            riskDaysAhead = riskDaysAhead == null ? 7 : riskDaysAhead;
            recommendationDaysBack = recommendationDaysBack == null ? 7 : recommendationDaysBack;
            if (dueDaysAhead < 0 || riskDaysAhead < 0 || recommendationDaysBack < 0) {
                throw new IllegalArgumentException("app.notification.events.* 일수는 0 이상이어야 합니다.");
            }
        }
    }
}
