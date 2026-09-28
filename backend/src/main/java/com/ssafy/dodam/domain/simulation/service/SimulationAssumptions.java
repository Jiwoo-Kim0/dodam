package com.ssafy.dodam.domain.simulation.service;

/**
 * 585 유형(예적금·대출)의 가정·사유 코드. 응답 `after.assumptions`와 `goalImpactDetails[].reasons`에 그대로 노출되므로
 * 이름을 바꾸면 FE 계약이 바뀐다. 각 코드의 뜻은 simulation.md에 있다.
 */
public final class SimulationAssumptions {
    // 공통
    /** 세금·수수료(중도상환·대환 부대비용 포함)를 계산하지 않았다. 0원 확정이 아니다. */
    public static final String EXCLUDES_TAX_AND_FEES = "EXCLUDES_TAX_AND_FEES";
    /** 상품 조건은 저장된 상품 DB 스냅샷(syncedAt)이며 실시간 조회가 아니다. */
    public static final String PRODUCT_DB_SNAPSHOT = "PRODUCT_DB_SNAPSHOT";
    /** 보유 대출 조건은 로컬 `loan` 스냅샷이며 요청 중 금융망을 호출하지 않았다. */
    public static final String LOAN_LOCAL_SNAPSHOT = "LOAN_LOCAL_SNAPSHOT";
    /** 같은 조건의 옵션이 여럿이라 정렬 정책(금리 오름차순·id 오름차순)으로 하나를 골랐다. */
    public static final String MULTIPLE_OPTIONS_LOWEST_RATE_SELECTED = "MULTIPLE_OPTIONS_LOWEST_RATE_SELECTED";

    // 예적금
    /** 단리·ACT/365 서비스 추정 모형. 복리 주기·이자지급 방식 컬럼이 없어 상품 실제 방식을 확인할 수 없다. */
    public static final String SIMPLE_INTEREST_ACT_365 = "SIMPLE_INTEREST_ACT_365";
    /** 우대금리·특별조건을 적용하지 않고 기본금리만 썼다. 가입 자격은 판정하지 않는다. */
    public static final String BASE_RATE_WITHOUT_PREFERENTIAL = "BASE_RATE_WITHOUT_PREFERENTIAL";
    /** 적금은 기준일 첫 납입 후 매월 같은 금액을 납입한다고 가정했다(자유적립형 포함). */
    public static final String FIXED_MONTHLY_INSTALLMENT = "FIXED_MONTHLY_INSTALLMENT";

    // 대출
    /** 공시 최저금리 시나리오. 예상 승인금리가 아니며 승인 가능 여부를 제공하지 않는다. */
    public static final String LOAN_RATE_PUBLISHED_MINIMUM = "LOAN_RATE_PUBLISHED_MINIMUM";
    /** 옵션 rate_type이 FIXED가 아니라(변동·혼합·미상) 현재 공시금리를 만기까지 고정으로 봤다. */
    public static final String VARIABLE_RATE_HELD_CONSTANT = "VARIABLE_RATE_HELD_CONSTANT";
    /** 선택된 옵션과 상품 모두 한도 자료가 없어 한도 검증을 생략했다. */
    public static final String PRODUCT_LIMIT_UNKNOWN = "PRODUCT_LIMIT_UNKNOWN";
    /** 옵션 상환 코드(AMORTIZING)가 두 균등 방식에 대응해 요청·기존 대출의 방식으로 해석했다. */
    public static final String REPAYMENT_CODE_AMBIGUOUS_MAPPED = "REPAYMENT_CODE_AMBIGUOUS_MAPPED";
    /** 월이율(연이율/12) 모형. 회차 이자는 직전 잔액 × 월이율 HALF_UP이며 일할 계산하지 않는다. */
    public static final String MONTHLY_RATE_NOT_DAILY_PRORATED = "MONTHLY_RATE_NOT_DAILY_PRORATED";
    /** 월중 추가상환까지의 경과이자를 따로 정산하지 않았다. 확정 중도상환 견적이 아니다. */
    public static final String NO_ACCRUED_INTEREST_SETTLEMENT = "NO_ACCRUED_INTEREST_SETTLEMENT";
    /** 추가상환 후 만기를 유지하고 남은 회차 상환액을 재계산했다(만기 단축 방식 아님). */
    public static final String MATURITY_KEPT_PAYMENT_RECALCULATED = "MATURITY_KEPT_PAYMENT_RECALCULATED";
    /** 대환은 기존 일정의 잔여 회차 수를 대상 옵션 term으로 맞췄다. 달력상 n개월 계약과 다를 수 있다. */
    public static final String REMAINING_SCHEDULE_AS_TERM = "REMAINING_SCHEDULE_AS_TERM";
    /** 부분 대환은 기존 금리 잔여분과 신규 금리분을 따로 계산해 합산했다. */
    public static final String PARTIAL_REFINANCE_SPLIT_SCHEDULES = "PARTIAL_REFINANCE_SPLIT_SCHEDULES";
    /** 현금흐름 표가 120개월 상한에서 잘렸다. 누적액은 표시 기간 합계이며 만기 합계가 아니다. */
    public static final String CASHFLOW_TABLE_TRUNCATED = "CASHFLOW_TABLE_TRUNCATED";

    // 목표
    /** 가입·상환 자금의 출처(어느 계좌·목표에서 빠지는지)를 알 수 없어 목표 달성일을 계산하지 않았다. */
    public static final String FUNDING_SOURCE_UNKNOWN = "FUNDING_SOURCE_UNKNOWN";
    /** 차입금·만기 수령액을 어느 목표에 귀속할지 알 수 없어 목표 달성일을 계산하지 않았다. */
    public static final String GOAL_ALLOCATION_UNKNOWN = "GOAL_ALLOCATION_UNKNOWN";
    /** 약정 상환일별 상환 절감·증가분을 남은 목표 금액 비례로 기존 저축속도에 반영했다. */
    public static final String REPAYMENT_DELTA_ALLOCATED_TO_GOALS = "REPAYMENT_DELTA_ALLOCATED_TO_GOALS";
    /** 절감액은 발생일 이후 첫 투영일(기준일 + k개월)에 반영한다. 월 단위 보수적 추정. */
    public static final String GOAL_EFFECT_AT_NEXT_PROJECTION_DATE = "GOAL_EFFECT_AT_NEXT_PROJECTION_DATE";
    /** 기존 저축속도가 0이고 예정 절감액을 모두 반영해도 미달성이라 날짜를 낼 수 없다. */
    public static final String SAVING_PACE_ZERO = "SAVING_PACE_ZERO";
    /** 기준일에 이미 달성한 목표. 전후 모두 기준일이다. */
    public static final String ALREADY_ACHIEVED = "ALREADY_ACHIEVED";

    // 카드(586: 할부·리볼빙·현금서비스)
    /** 금리·수수료율·결제비율은 사용자 입력이며 카드 DB 값이나 실제 약정으로 검증하지 않았다. */
    public static final String CARD_TERMS_USER_INPUT = "CARD_TERMS_USER_INPUT";
    /** 연체료·추가 이용·금리 변동·기관별 최소결제액을 반영하지 않았다. 행동 전후의 현금 변화만 계산한다. */
    public static final String NO_LATE_FEES_OR_ADDITIONAL_USAGE = "NO_LATE_FEES_OR_ADDITIONAL_USAGE";
    /** 할부 수수료율을 생략해 0%로 계산했다. 실제 무이자 혜택을 확인한 것이 아니다. */
    public static final String ZERO_FEE_ASSUMED = "ZERO_FEE_ASSUMED";
    /** 리볼빙이 120회차 상한까지 완납되지 않았다. 잔액·이후 이자는 미상이며 0으로 채우지 않는다. */
    public static final String REVOLVING_HORIZON_LIMIT = "REVOLVING_HORIZON_LIMIT";

    // 복합(586)
    /** 자식 시나리오는 같은 기준선에서 독립 계산했고 앞 자식의 결과를 다음 자식에 잇지 않았다. 현금흐름은 자식 delta 합이다. */
    public static final String CHILD_SCENARIOS_INDEPENDENT = "CHILD_SCENARIOS_INDEPENDENT";
    /** 금융비용은 공통 평가기간에 납부일이 포함된 이자·수수료 합이며 평생 총비용이 아니다. */
    public static final String FINANCE_COST_EVALUATED = "FINANCE_COST_EVALUATED";
    /** 미완납 자식이 있어 평가기간 비용이 계산된 부분의 합계다(costComplete=false). */
    public static final String FINANCE_COST_PARTIAL = "FINANCE_COST_PARTIAL";
    /** 복합 목표 투영은 각 자식의 durationMonths 안에서만 변경을 적용하고 종료 후 기준선 속도로 복귀했다. */
    public static final String GOAL_EFFECT_LIMITED_TO_SCENARIO_DURATION = "GOAL_EFFECT_LIMITED_TO_SCENARIO_DURATION";
    /** SAVING 입력은 변경 후 절대 저축액이므로 그 구간의 SPENDING 절감액을 목표에 다시 배분하지 않았다. */
    public static final String SAVING_AMOUNT_IS_FINAL_TARGET = "SAVING_AMOUNT_IS_FINAL_TARGET";
    /** 소비·저축 외 자식이 있어 목표 귀속을 안전하게 합성할 수 없다. 자식별 단독 목표 영향은 details.children에 있다. */
    public static final String COMBINED_GOAL_EFFECT_NOT_COMPUTABLE = "COMBINED_GOAL_EFFECT_NOT_COMPUTABLE";

    private SimulationAssumptions() {
    }
}
