package com.ssafy.dodam.domain.coach.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 코치 도메인 정책값({@code app.coach}). 외부 공급자 접속 설정은 {@code app.ai}에 따로 있다.
 *
 * @param cacheEnabled 응답 캐시 사용 여부. 끄면 매 호출마다 외부 API를 부른다(= 매번 과금)
 * @param cacheTtl     보관 시간. <b>무효화 수단이 아니다</b> — 저장 데이터가 바뀌면 캐시 키 자체가
 *                     달라지므로({@link CoachCacheKey}) 낡은 문장이 나갈 일은 구조적으로 없다.
 *                     이 값은 "어쩌다 이상한 문장이 생성됐을 때 얼마나 오래 남는가"의 상한이다
 */
@ConfigurationProperties(prefix = "app.coach")
public record CoachProperties(Boolean cacheEnabled, Duration cacheTtl) {

    public CoachProperties {
        cacheEnabled = cacheEnabled == null || cacheEnabled;
        cacheTtl = cacheTtl == null || cacheTtl.isZero() || cacheTtl.isNegative() ? Duration.ofHours(1) : cacheTtl;
    }
}
