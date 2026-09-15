package com.ssafy.dodam.domain.simulation.service;

import com.ssafy.dodam.domain.simulation.dto.SimulationBaseline;
import com.ssafy.dodam.domain.simulation.dto.SimulationInput;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult;
import com.ssafy.dodam.domain.simulation.entity.Simulation;
import com.ssafy.dodam.domain.simulation.repository.SimulationUserLock;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시뮬레이션 실행: 사용자 행 잠금 → 기준일 기반 생략값 확정 → 요청 지문 재사용 확인 → 기준선 읽기 → 유형별 스냅샷 준비 → 계산 → 저장.
 *
 * <p>전부 한 트랜잭션이다. 계산은 순수 함수라 잠금 안에서 돌려도 외부 대기가 없고, 같은 사용자의 동시 요청이 직렬화되어
 * 같은 지문이 두 번 저장되지 않는다. 계산이 예외로 끝나면 트랜잭션이 롤백되어 행이 남지 않는다.
 *
 * <p>상품·대출 스냅샷은 지문 조회 <b>뒤</b>에 읽는다(585). 같은 날 같은 요청은 상품 옵션이 바뀌어도 첫 결과를 돌려주고,
 * 옵션 검증·상환방식 자동 선택은 신규 계산에서만 일어난다. 대출 소유권은 Resolver가 지문 전에 이미 확인했다.
 *
 * <p>복합(586)도 부모 한 건에 대해 이 순서를 한 번만 밟는다. 자식 계산은 {@link SimulationCalculatorRegistry}를 통해
 * 복합 계산기 안에서 돌고, 자식 하나라도 실패하면 부모·자식 행이 모두 저장되지 않는다.
 */
@Service
public class SimulationRunner {
    private final SimulationCalculatorRegistry calculators;
    private final SimulationUserLock users;
    private final SimulationBaselineReader baselineReader;
    private final SimulationSnapshotReader snapshotReader;
    private final SimulationStore store;
    private final SimulationClock clock;

    public SimulationRunner(SimulationCalculatorRegistry calculators, SimulationUserLock users,
            SimulationBaselineReader baselineReader, SimulationSnapshotReader snapshotReader, SimulationStore store,
            SimulationClock clock) {
        this.calculators = calculators;
        this.users = users;
        this.baselineReader = baselineReader;
        this.snapshotReader = snapshotReader;
        this.store = store;
        this.clock = clock;
    }

    public record Outcome(Simulation simulation, boolean created) { }

    @Transactional
    public Outcome run(Long userId, SimulationInput input) {
        calculators.require(input.type());
        users.lock(userId).orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        LocalDateTime now = clock.now();
        LocalDate today = now.toLocalDate();
        input = input.withBaseDate(today);
        String requestHash = SimulationRequestHash.of(userId, input.type(), input.fingerprint(), today,
                SimulationCalculationVersion.CURRENT);
        Optional<Simulation> reusable = store.findReusable(userId, input.type(), requestHash, today);
        if (reusable.isPresent()) {
            return new Outcome(reusable.get(), false);
        }
        SimulationBaseline baseline = baselineReader.read(userId, today);
        SimulationInput prepared = snapshotReader.prepare(userId, input);
        SimulationResult result = calculators.compute(baseline, prepared).result();
        // 저장 파라미터는 준비 전 입력과 같다(스냅샷은 parameters()에 들어가지 않는다). 계산에 쓴 스냅샷은 result.details에 남는다
        return new Outcome(store.save(userId, prepared, requestHash, baseline, result, now), true);
    }
}
