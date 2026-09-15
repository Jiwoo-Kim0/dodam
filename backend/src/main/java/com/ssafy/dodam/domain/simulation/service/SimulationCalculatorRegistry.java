package com.ssafy.dodam.domain.simulation.service;

import com.ssafy.dodam.domain.simulation.dto.ScenarioComputation;
import com.ssafy.dodam.domain.simulation.dto.SimulationBaseline;
import com.ssafy.dodam.domain.simulation.dto.SimulationInput;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 유형 → 계산기 조회와 순수 dispatch. Runner와 복합 계산기가 공유한다 — 복합 계산이 Runner.run(잠금·지문·저장)을
 * 재호출하지 않고 자식 계산만 돌리기 위해 분리했다.
 *
 * <p>계산기 목록은 첫 조회 때 채운다. 복합 계산기 자체가 이 레지스트리를 주입받으므로 생성자에서 계산기 빈을 모으면
 * 순환 참조가 된다. 계산기 빈이 하나도 없어도(공통 모델만 있는 배포) 기동은 되고 실행 요청만 거부한다.
 */
@Component
public class SimulationCalculatorRegistry {
    private final Supplier<Collection<SimulationCalculator<?>>> source;
    private volatile Map<SimulationType, SimulationCalculator<?>> calculators;

    @Autowired
    public SimulationCalculatorRegistry(ObjectProvider<SimulationCalculator<?>> provider) {
        this(() -> provider.orderedStream().toList());
    }

    public SimulationCalculatorRegistry(Supplier<Collection<SimulationCalculator<?>>> source) {
        this.source = source;
    }

    public SimulationCalculator<?> require(SimulationType type) {
        SimulationCalculator<?> calculator = calculators().get(type);
        if (calculator == null) {
            throw new BusinessException(ErrorCode.SIMULATION_TYPE_NOT_SUPPORTED);
        }
        return calculator;
    }

    public ScenarioComputation compute(SimulationBaseline baseline, SimulationInput input) {
        return compute(require(input.type()), baseline, input);
    }

    private static <Q extends SimulationInput> ScenarioComputation compute(SimulationCalculator<Q> calculator,
            SimulationBaseline baseline, SimulationInput input) {
        return calculator.compute(baseline, calculator.inputType().cast(input));
    }

    private Map<SimulationType, SimulationCalculator<?>> calculators() {
        Map<SimulationType, SimulationCalculator<?>> loaded = calculators;
        if (loaded == null) {
            synchronized (this) {
                loaded = calculators;
                if (loaded == null) {
                    loaded = new EnumMap<>(SimulationType.class);
                    for (SimulationCalculator<?> calculator : source.get()) {
                        if (loaded.put(calculator.type(), calculator) != null) {
                            throw new IllegalStateException("같은 유형의 계산기가 둘 이상입니다: " + calculator.type());
                        }
                    }
                    calculators = loaded;
                }
            }
        }
        return loaded;
    }
}
