package com.ssafy.dodam.global.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 기동 직후 Redis에 한 번 PING을 보내고 결과를 로그로 남긴다.
 *
 * <p><b>왜 필요한가</b>: Redis를 쓰는 두 곳(코치 응답 캐시, 멱등 키 가드)은 모두 장애를 삼킨다. 옳은 선택이지만
 * 부작용이 있다 — 비밀번호나 호스트가 틀려도 겉으로는 아무 일도 일어나지 않는다. 캐시가 100% 미스로
 * 동작하면 응답은 정상이고 요금만 는다. 실패 WARN이 있긴 하지만 <b>그 경로를 아무도 호출하지 않으면
 * 로그도 비어 있어</b>, "로그에 오류가 없다"가 "정상이다"를 뜻하지 않는다.
 *
 * <p>이 한 줄이 그 모호함을 없앤다. 배포마다 {@code docker logs backend | grep "Redis "} 한 번으로
 * 연결 상태를 확인할 수 있고, "쓰지 않아서 조용한 것"과 "붙어서 조용한 것"이 구분된다.
 *
 * <p>실패해도 기동은 막지 않는다. Redis는 이 서비스의 어떤 기능에도 필수가 아니다 — 캐시가 없으면 비싸고,
 * 멱등 키가 없으면 재전송 보호가 약해질 뿐 화면은 전부 동작한다. 기동을 막으면 그 대가가 훨씬 크다.
 */
@Component
@ConditionalOnProperty(name = "app.redis.startup-check.enabled", havingValue = "true", matchIfMissing = true)
public class RedisStartupCheck {

    private static final Logger log = LoggerFactory.getLogger(RedisStartupCheck.class);

    private final StringRedisTemplate redis;

    public RedisStartupCheck(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ping() {
        try {
            // 캐스팅이 필요한 이유: execute(...)는 RedisCallback과 SessionCallback 두 오버로드를 가진다.
            String reply = redis.execute((RedisCallback<String>) RedisConnection::ping);
            log.info("Redis is reachable (ping={}). Coach answer cache and idempotency guard are backed by Redis.", reply);
        } catch (Exception exception) {
            // ERROR가 아니라 WARN이다. 서비스는 계속 돌고, 대가는 비용과 보호 수준이지 기능이 아니다.
            log.warn("Redis is NOT reachable: {}. The coach answer cache will miss on every call (each call is billed)"
                    + " and the idempotency guard will pass requests through unprotected.", exception.getMessage());
        }
    }
}
