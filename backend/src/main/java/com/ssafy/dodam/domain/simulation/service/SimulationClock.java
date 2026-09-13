package com.ssafy.dodam.domain.simulation.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** 기준일·저장 시각의 단일 출처. 테스트가 대체한다. DiagnosisClock과 같은 방식. */
@Component
public class SimulationClock {
    public LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}
