package com.ssafy.dodam.domain.simulation.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 실행 시점의 사용자 현황. 계산기는 이 값만 보고 결과를 만들며 DB를 읽지 않는다.
 *
 * @param baseDate               기준일. 소비 기준 구간과 목표 예상일 계산의 출발점
 * @param monthlyIncome          `financial_profile.monthly_income`. 없으면 null(저축액 상한 검증 생략)
 * @param averageMonthlySpending 기준일 직전 3개 달력월의 월평균 지출(원). 거래가 없으면 0
 * @param spendingByCategory     같은 구간의 카테고리 코드별 월평균 지출. 미분류는 {@code UNCATEGORIZED}
 * @param savingGoals            활성 모으기 목표. 없으면 빈 목록
 * @param spendingGoals          활성 줄이기 목표. 없으면 빈 목록
 * @param coverage               누락된 입력의 사유 코드(예: {@code SPENDING_NO_TRANSACTIONS}, {@code INCOME_UNKNOWN})
 */
public record SimulationBaseline(
        LocalDate baseDate,
        Long monthlyIncome,
        long averageMonthlySpending,
        Map<String, Long> spendingByCategory,
        List<SavingGoal> savingGoals,
        List<SpendingGoal> spendingGoals,
        List<String> coverage
) {
    public static final String SPENDING_NO_TRANSACTIONS = "SPENDING_NO_TRANSACTIONS";
    public static final String INCOME_UNKNOWN = "INCOME_UNKNOWN";
    public static final String NO_ACTIVE_SAVING_GOAL = "NO_ACTIVE_SAVING_GOAL";

    public SimulationBaseline {
        spendingByCategory = Map.copyOf(spendingByCategory);
        savingGoals = List.copyOf(savingGoals);
        spendingGoals = List.copyOf(spendingGoals);
        coverage = List.copyOf(coverage);
    }

    /** 현재 월 저축 속도 합. 목표가 없으면 0. */
    public long averageMonthlySaving() {
        return savingGoals.stream().mapToLong(SavingGoal::averageMonthlySaving).sum();
    }

    /**
     * 모으기 목표 기준선. Goal 도메인의 진행률·예상일 계산과 같은 정의다.
     *
     * @param currentAmount           연결 계좌 잔액 합(없으면 0)
     * @param remaining               {@code max(0, target − current)}
     * @param averageMonthlySaving    {@code current / max(1, 생성일부터 기준일까지 경과 개월)}
     * @param expectedAchievementDate 현재 속도 기준 예상 달성일. 속도 0이면 null
     * @param linkedAccountIds        연결 계좌 id. 계좌 지정 시뮬레이션이 영향 대상을 좁히는 데 쓴다
     */
    public record SavingGoal(
            Long goalId,
            String name,
            long targetAmount,
            long currentAmount,
            long remaining,
            long averageMonthlySaving,
            LocalDate targetDate,
            LocalDate expectedAchievementDate,
            List<Long> linkedAccountIds
    ) {
        public SavingGoal {
            linkedAccountIds = List.copyOf(linkedAccountIds);
        }
    }

    /**
     * 줄이기 목표 기준선.
     *
     * @param spentThisMonth      기준월 1일부터 기준일까지 지출
     * @param projectedMonthEnd   Goal과 같은 식: {@code spent × 월 일수 / 경과 일수}(반올림)
     */
    public record SpendingGoal(Long goalId, String name, long monthlyBudget, long spentThisMonth, long projectedMonthEnd) {
    }
}
