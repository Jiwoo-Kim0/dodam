package com.ssafy.dodam.domain.simulation.service;

import com.ssafy.dodam.domain.simulation.dto.SimulationBaseline;
import com.ssafy.dodam.domain.simulation.dto.SimulationInput;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult;
import com.ssafy.dodam.domain.simulation.entity.Simulation;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import com.ssafy.dodam.domain.simulation.repository.SimulationUserLock;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시뮬레이션 실행: 사용자 행 잠금 → 요청 지문 재사용 확인 → 기준선 읽기 → 계산 → 저장.
 *
 * <p>전부 한 트랜잭션이다. 계산은 순수 함수라 잠금 안에서 돌려도 외부 대기가 없고, 같은 사용자의 동시 요청이 직렬화되어
 * 같은 지문이 두 번 저장되지 않는다. 계산이 예외로 끝나면 트랜잭션이 롤백되어 행이 남지 않는다.
 */
@Service
public class SimulationRunner {
    private final Map<SimulationType, SimulationCalculator<?>> calculators = new EnumMap<>(SimulationType.class);
    private final SimulationUserLock users;
    private final SimulationBaselineReader baselineReader;
    private final SimulationStore store;
    private final SimulationClock clock;

    // ObjectProvider: 계산기 빈이 하나도 없어도(공통 모델만 있는 배포) 기동은 되고 실행 요청만 거부한다
    public SimulationRunner(ObjectProvider<SimulationCalculator<?>> calculators, SimulationUserLock users,
            SimulationBaselineReader baselineReader, SimulationStore store, SimulationClock clock) {
        for (SimulationCalculator<?> calculator : calculators.orderedStream().toList()) {
            if (this.calculators.put(calculator.type(), calculator) != null) {
                throw new IllegalStateException("같은 유형의 계산기가 둘 이상입니다: " + calculator.type());
            }
        }
        this.users = users;
        this.baselineReader = baselineReader;
        this.store = store;
        this.clock = clock;
    }

    public record Outcome(Simulation simulation, boolean created) { }

    @Transactional
    public Outcome run(Long userId, SimulationInput input) {
        SimulationCalculator<?> calculator = calculators.get(input.type());
        if (calculator == null) {
            throw new BusinessException(ErrorCode.SIMULATION_TYPE_NOT_SUPPORTED);
        }
        users.lock(userId).orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        LocalDateTime now = clock.now();
        LocalDate today = now.toLocalDate();
        String requestHash = SimulationRequestHash.of(userId, input.type(), input.fingerprint(), today,
                SimulationCalculationVersion.CURRENT);
        Optional<Simulation> reusable = store.findReusable(userId, input.type(), requestHash, today);
        if (reusable.isPresent()) {
            return new Outcome(reusable.get(), false);
        }
        SimulationBaseline baseline = baselineReader.read(userId, today);
        SimulationResult result = calculate(calculator, baseline, input);
        return new Outcome(store.save(userId, input, requestHash, baseline, result, now), true);
    }

    private static <Q extends SimulationInput> SimulationResult calculate(SimulationCalculator<Q> calculator,
            SimulationBaseline baseline, SimulationInput input) {
        return calculator.calculate(baseline, calculator.inputType().cast(input));
    }
}
