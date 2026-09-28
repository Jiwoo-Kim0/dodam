package com.ssafy.dodam.domain.minusaccount.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * 이자 계산과 상환 처리 시각의 기준일.
 *
 * <p>seed의 `next_interest_date`가 고정 날짜라 기준일을 코드 상수로 두면 테스트가 특정 시점에만
 * 통과한다. 주입 가능한 컴포넌트로 두어 테스트에서 기준일을 바꿀 수 있게 한다.
 */
@Component
public class MinusAccountClock {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    public LocalDate today() {
        return LocalDate.now(SERVICE_ZONE);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(SERVICE_ZONE);
    }
}
