package com.ssafy.dodam.domain.simulation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 계산기의 출력. 저장 계층이 그대로 JSON으로 직렬화해 `simulation` 행과 `simulation_goal_impact` 행을 만든다.
 * 계산기는 여기 담긴 수치 외의 부작용을 만들지 않는다.
 *
 * @param title        사용자에게 보이는 한 줄 제목(≤255)
 * @param summary      결과 요약(≤500)
 * @param before       기준선 스냅샷. 조회 API가 재계산 없이 돌려준다
 * @param after        적용 후 스냅샷
 * @param comparison   항목별 전후 비교. {@link Comparison}
 * @param cashflow     월별 가용자금 변화. {@link Cashflow}
 * @param goalImpacts  목표별 예상 달성일 변화
 * @param assumptions  계산에 적용한 가정 코드. 응답에 그대로 노출한다
 * @param details      유형별 조건·계산치(상품 스냅샷, 적용 금리, 전후 이자 등). 585 유형만 채우며 `after_snapshot.details`에 저장된다.
 *                     null이면 저장하지 않는다(기존 spending/saving 계약 유지)
 * @param goalImpactDetails 목표별 계산 상태·사유. null이면 저장하지 않는다. 엔티티에는 날짜만 저장되므로 사유는 여기서만 읽는다
 */
public record SimulationResult(
        String title,
        String summary,
        Snapshot before,
        Snapshot after,
        Comparison comparison,
        Cashflow cashflow,
        List<GoalImpact> goalImpacts,
        List<String> assumptions,
        Map<String, Object> details,
        List<GoalImpactDetail> goalImpactDetails
) {
    public SimulationResult {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(comparison, "comparison");
        Objects.requireNonNull(cashflow, "cashflow");
        goalImpacts = List.copyOf(goalImpacts);
        assumptions = List.copyOf(assumptions);
        // LinkedHashMap 복사: 문서·응답에서 항목 순서가 유지되고 null 값도 그대로 남는다(Map.copyOf는 null을 거부한다)
        details = details == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(details));
        goalImpactDetails = goalImpactDetails == null ? null : List.copyOf(goalImpactDetails);
    }

    /** 550·551 유형의 생성자. 확장 필드 없음. */
    public SimulationResult(String title, String summary, Snapshot before, Snapshot after, Comparison comparison,
            Cashflow cashflow, List<GoalImpact> goalImpacts, List<String> assumptions) {
        this(title, summary, before, after, comparison, cashflow, goalImpacts, assumptions, null, null);
    }

    /**
     * 전·후 공통 스냅샷 모양. 유형별로 의미 없는 항목은 null이다.
     *
     * @param monthlySpending       월 지출
     * @param monthlySaving         월 저축
     * @param monthlyAvailableFunds 월 가용자금 {@code income − spending − saving}. 소득을 모르면 null
     * @param goals                 목표별 예상 달성일
     */
    public record Snapshot(
            LocalDate baseDate,
            Long monthlyIncome,
            long monthlySpending,
            long monthlySaving,
            Long monthlyAvailableFunds,
            List<GoalForecast> goals
    ) {
        public Snapshot {
            goals = List.copyOf(goals);
        }
    }

    public record GoalForecast(Long goalId, String name, long remaining, long monthlySaving, LocalDate expectedAchievementDate) {
    }

    /**
     * 비교표. {@code items}는 요약 항목, {@code assets}·{@code debts}·{@code costs}는 명세 17의 자산·부채·금융비용 변화 조회에
     * 페이지로 잘라 돌려주는 목록이다. 유형이 다루지 않는 목록은 비어 있다.
     */
    public record Comparison(List<Item> items, List<Item> assets, List<Item> debts, List<Item> costs) {
        public Comparison {
            items = List.copyOf(items);
            assets = List.copyOf(assets);
            debts = List.copyOf(debts);
            costs = List.copyOf(costs);
        }
    }

    /**
     * @param key    기계용 식별자(예: {@code MONTHLY_SPENDING}, {@code GOAL_12_EXPECTED_DATE})
     * @param before 전 값. 금액은 원, 날짜는 ISO 문자열, 없으면 null
     * @param delta  {@code after − before}. 수치가 아니거나 한쪽이 없으면 null
     */
    public record Item(String key, String label, Object before, Object after, Long delta) {
        public static Item amount(String key, String label, Long before, Long after) {
            Long delta = before == null || after == null ? null : after - before;
            return new Item(key, label, before, after, delta);
        }

        /** 날짜 항목의 delta는 일수 차이다. 한쪽이 null(달성 불가)이면 null. */
        public static Item date(String key, String label, LocalDate before, LocalDate after) {
            Long delta = before == null || after == null ? null : ChronoUnit.DAYS.between(before, after);
            return new Item(key, label, before == null ? null : before.toString(),
                    after == null ? null : after.toString(), delta);
        }
    }

    /**
     * 월별 변화.
     *
     * @param basis   변화의 기준. spending/saving은 {@code MONTHLY_AVAILABLE_FUNDS}(월 가용자금 수준),
     *                585 유형은 {@code SCENARIO_NET_CASH_CHANGE}(시나리오가 만드는 순현금 변화. 사용자 전체 가용잔액이 아니다)
     * @param before  기준선 월 가용자금. 소득을 모르거나 SCENARIO 기준이면 null
     * @param after   적용 후 월 가용자금. 소득을 모르거나 SCENARIO 기준이면 null
     * @param months  기준일 다음 달부터의 월별 변화. 예정 이벤트를 다시 더하지 않는다
     * @param currentMonth 기준일부터 당월 말까지의 변화(가입 원금, 당일 추가상환 등). 585 유형만. {@code cumulative}는 여기부터 누적한다
     * @param truncated    표가 120개월 상한에서 끝나 전체 기간을 담지 못했는지. 585 유형만
     * @param coveredThrough 표의 마지막 yearMonth. 585 유형만
     * @param fullTermEndDate 시나리오 전체 기간의 종료일(만기). 585 유형만
     */
    public record Cashflow(String basis, Long before, Long after, List<Month> months,
            @JsonInclude(JsonInclude.Include.NON_NULL) CurrentMonth currentMonth,
            @JsonInclude(JsonInclude.Include.NON_NULL) Boolean truncated,
            @JsonInclude(JsonInclude.Include.NON_NULL) String coveredThrough,
            @JsonInclude(JsonInclude.Include.NON_NULL) LocalDate fullTermEndDate) {
        public static final String MONTHLY_AVAILABLE_FUNDS = "MONTHLY_AVAILABLE_FUNDS";
        public static final String SCENARIO_NET_CASH_CHANGE = "SCENARIO_NET_CASH_CHANGE";

        public Cashflow {
            months = List.copyOf(months);
        }

        /** 550·551 유형의 생성자. 확장 필드 없음. */
        public Cashflow(String basis, Long before, Long after, List<Month> months) {
            this(basis, before, after, months, null, null, null, null);
        }
    }

    /** @param cumulative 해당 월까지의 누적 delta */
    public record Month(String yearMonth, long delta, long cumulative) {
    }

    public record CurrentMonth(String yearMonth, long delta) {
    }

    public record GoalImpact(Long goalId, LocalDate before, LocalDate after) {
    }

    /**
     * 목표별 계산 상태. {@code COMPUTED}는 날짜 투영이 이뤄진 것(변화 0 포함), {@code NOT_COMPUTABLE}은 자금 출처·목표 귀속을
     * 알 수 없거나 속도 0으로 날짜를 낼 수 없는 것이다. 사유 코드는 계산기 상수를 그대로 쓴다.
     */
    public record GoalImpactDetail(Long goalId, String status, List<String> reasons) {
        public static final String COMPUTED = "COMPUTED";
        public static final String NOT_COMPUTABLE = "NOT_COMPUTABLE";

        public GoalImpactDetail {
            reasons = List.copyOf(reasons);
        }
    }
}
