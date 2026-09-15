package com.ssafy.dodam.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    DIAGNOSIS_IN_PROGRESS(HttpStatus.CONFLICT, "진단이 이미 진행 중입니다."),
    DIAGNOSIS_EXPIRED(HttpStatus.CONFLICT, "진단 실행이 만료되었습니다. 다시 요청해 주세요."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    /**
     * SSAFY 금융망에 다른 서비스가 먼저 등록한 이메일 (API 문서 2.2.2 안내에 따라 다른 이메일 사용 유도).
     */
    FINANCE_EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "다른 서비스에서 이미 사용 중인 이메일입니다. 다른 이메일을 사용해 주세요."),
    /**
     * 이메일이 없는 경우와 비밀번호가 틀린 경우를 구분하지 않는다(계정 존재 여부 노출 방지).
     */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "인증 토큰이 유효하지 않습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "인증 토큰이 만료되었습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh Token이 유효하지 않습니다."),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인 세션이 만료되었습니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "세션을 찾을 수 없습니다."),

    // ---------- 소셜 로그인 (명세 외 확장, backend/docs/auth-social.md) ----------
    SOCIAL_PROVIDER_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인 공급자입니다."),
    /**
     * 만료·위조·다른 앱에서 발급된 토큰. 세부 원인은 서버 로그에만 남긴다.
     */
    SOCIAL_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "소셜 로그인 토큰이 유효하지 않습니다."),
    SOCIAL_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "소셜 로그인 공급자에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    /**
     * 공급자 이메일과 같은 이메일의 회원에게 이미 같은 공급자의 다른 소셜 계정이 연결되어 있다.
     */
    SOCIAL_ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT, "해당 이메일의 계정에는 이미 같은 공급자의 소셜 계정이 연결되어 있습니다."),
    USER_WITHDRAWN(HttpStatus.FORBIDDEN, "탈퇴한 계정입니다."),

    INVALID_TIMEZONE(HttpStatus.BAD_REQUEST, "지원하지 않는 타임존입니다."),
    NO_FIELDS_TO_UPDATE(HttpStatus.BAD_REQUEST, "수정할 항목이 없습니다."),

    // ---------- Finance Data (S15P21D105-498 ~ 500) ----------
    FINANCE_CONNECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "금융망 연결 정보가 없습니다."),
    FINANCE_ALREADY_CONNECTED(HttpStatus.CONFLICT, "이미 금융망에 연결되어 있습니다."),
    FINANCE_NOT_CONNECTED(HttpStatus.CONFLICT, "금융망에 연결되어 있지 않습니다. 먼저 금융망 연결을 완료해 주세요."),
    FINANCE_SYNC_IN_PROGRESS(HttpStatus.CONFLICT, "금융 데이터 동기화가 이미 진행 중입니다."),
    FINANCE_SYNC_NOT_FOUND(HttpStatus.NOT_FOUND, "동기화 이력이 없습니다."),
    /**
     * 금융망이 오류 응답(responseCode)을 돌려준 경우. 상세 코드는 서버 로그에만 남긴다.
     */
    FINANCE_API_ERROR(HttpStatus.BAD_GATEWAY, "금융망 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),
    /**
     * 금융망에 도달하지 못한 경우(타임아웃·연결 실패·응답 해석 불가).
     */
    FINANCE_API_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "금융망에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."),

    /**
     * 마이너스통장 상환·이자납부(S15P21D105-534)의 상태 전이 검증.
     * 계좌가 존재하지 않거나 타인 소유인 경우는 리소스 존재 여부를 감추기 위해 RESOURCE_NOT_FOUND를 쓴다.
     */
    MINUS_ACCOUNT_NOT_ACTIVE(HttpStatus.CONFLICT, "사용할 수 없는 상태의 계좌입니다."),
    REPAYMENT_AMOUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "상환 가능 금액을 초과했습니다."),
    NO_ACCRUED_INTEREST(HttpStatus.BAD_REQUEST, "납부할 미수이자가 없습니다."),
    INSUFFICIENT_ACCOUNT_BALANCE(HttpStatus.CONFLICT, "출금계좌 잔액이 부족합니다."),
    WITHDRAWAL_ACCOUNT_REQUIRED(HttpStatus.BAD_REQUEST, "출금계좌를 지정해 주세요."),

    // ---------- Transaction (S15P21D105-505) ----------
    /**
     * 금융망·seed 출처 거래를 수정·삭제하려는 경우. 거래 존재 여부는 이미 확인된 뒤라 404가 아니라 400이다.
     */
    TRANSACTION_NOT_MANUAL(HttpStatus.BAD_REQUEST, "수동으로 등록한 거래만 수정·삭제할 수 있습니다."),

    // ---------- Goal (S15P21D105-508) ----------
    /**
     * 종료 상태(ACHIEVED·CANCELED) 목표를 수정하려는 경우.
     */
    GOAL_NOT_EDITABLE(HttpStatus.CONFLICT, "종료된 목표는 수정할 수 없습니다."),
    /**
     * 허용되지 않은 상태 전이(예: PAUSED → ACHIEVED). 전이 규칙은 GoalStatus에 있다.
     */
    GOAL_STATUS_TRANSITION_NOT_ALLOWED(HttpStatus.CONFLICT, "허용되지 않은 목표 상태 변경입니다."),
    /**
     * 계좌는 한 목표에만 연결할 수 있다(`uk_goal_account_account`). 다른 목표에 이미 연결된 계좌를 지정한 경우(S15P21D105-509).
     */
    ACCOUNT_ALREADY_LINKED_TO_GOAL(HttpStatus.CONFLICT, "이미 다른 목표에 연결된 계좌입니다."),

    // ---------- Subscription (S15P21D105-514) ----------
    /**
     * 종료 상태(CANCELED·EXPIRED) 정기결제를 수정·정지·재개·취소하려는 경우.
     */
    SUBSCRIPTION_NOT_EDITABLE(HttpStatus.CONFLICT, "종료된 정기결제는 변경할 수 없습니다."),
    /**
     * 허용되지 않은 상태 전이(예: ACTIVE → EXPIRED 직접 지정). 전이 규칙은 SubscriptionStatus에 있다.
     */
    SUBSCRIPTION_STATUS_TRANSITION_NOT_ALLOWED(HttpStatus.CONFLICT, "허용되지 않은 정기결제 상태 변경입니다."),

    // ---------- Recommendation (S15P21D105-548, 549) ----------
    /**
     * 아직 완료되지 않은 진단으로 개선안을 만들려는 경우. 진단은 존재하므로 404가 아니라 409다.
     */
    DIAGNOSIS_NOT_COMPLETED(HttpStatus.CONFLICT, "완료된 진단이 아닙니다."),
    /**
     * 요청 goalId가 진단의 goalId와 다른 경우. 다른 목표로 진단한 근거를 이 목표의 개선안에 쓰면 목표기여 계산이 틀어진다.
     */
    RECOMMENDATION_GOAL_MISMATCH(HttpStatus.BAD_REQUEST, "진단의 목표와 다른 목표를 지정할 수 없습니다."),
    /**
     * 허용되지 않은 개선안 상태 전이(같은 상태 재요청 포함). 전이 규칙은 RecommendationTransitionPolicy에 있다.
     */
    RECOMMENDATION_STATUS_TRANSITION_NOT_ALLOWED(HttpStatus.CONFLICT, "허용되지 않은 개선안 상태 변경입니다."),

    // ---------- Simulation (S15P21D105-550, 551) ----------
    /**
     * 월 감소액이 기준 구간(직전 3개월)의 월평균 지출을 넘는 경우. 줄일 수 없는 금액을 줄인 결과를 만들지 않는다.
     */
    SIMULATION_REDUCTION_EXCEEDS_SPENDING(HttpStatus.BAD_REQUEST, "월 감소액이 최근 월평균 지출을 초과합니다."),
    /**
     * 새 월 저축액이 등록된 월 소득을 넘는 경우. 소득이 등록되지 않았으면 검사하지 않는다.
     */
    SIMULATION_SAVING_EXCEEDS_INCOME(HttpStatus.BAD_REQUEST, "월 저축액이 월 소득을 초과합니다."),
    /**
     * 계산기가 등록되지 않은 시뮬레이션 유형. 공개 경로는 유형별로 열려 있어 정상 흐름에서는 발생하지 않는다.
     */
    SIMULATION_TYPE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "지원하지 않는 시뮬레이션 유형입니다."),

    // ---------- Simulation 예적금·대출 (S15P21D105-585) ----------
    /**
     * 상품은 존재하지만 시뮬레이션 대상 유형이 아닌 경우(예금은 FIXED_DEPOSIT만, 대출은 분할상환 비교군만) 또는
     * 대환 대상 상품이 기존 대출의 비교군과 다른 경우. 상품 존재 여부는 이미 확인된 뒤라 404가 아니라 400이다.
     */
    SIMULATION_PRODUCT_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "시뮬레이션을 지원하지 않는 상품입니다."),
    /**
     * 요청 기간·상환방식을 만족하고 금리 자료가 유효한 상품 옵션이 없는 경우. 인접 기간이나 상품 최상위 금리로 대체하지 않는다.
     */
    SIMULATION_PRODUCT_OPTION_UNAVAILABLE(HttpStatus.BAD_REQUEST, "요청 조건에 맞는 상품 옵션이 없습니다."),
    /**
     * 신규 대출의 상환방식을 생략했지만 상품 옵션만으로 하나로 확정할 수 없는 경우(AMORTIZING 등).
     */
    SIMULATION_REPAYMENT_TYPE_REQUIRED(HttpStatus.BAD_REQUEST, "상환방식을 지정해 주세요. 상품 조건만으로 확정할 수 없습니다."),
    /**
     * 유효한 옵션이 모두 공시 한도 때문에 탈락한 경우. 한도를 모르는 옵션은 탈락시키지 않는다.
     */
    SIMULATION_PRODUCT_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "대출 금액이 상품의 공시 한도를 초과합니다."),
    /**
     * 보유 대출의 상태·잔액·금리·만기·다음 상환일 자료가 계산에 부족한 경우(OVERDUE, 잔액 0, 당일 대환 등). 0원으로 대체하지 않는다.
     */
    SIMULATION_LOAN_DATA_UNAVAILABLE(HttpStatus.BAD_REQUEST, "대출 자료가 계산에 충분하지 않습니다."),
    /** 추가상환일이 기준일 이전이거나 만기 이후인 경우. */
    SIMULATION_INVALID_PAYMENT_DATE(HttpStatus.BAD_REQUEST, "추가상환일은 기준일부터 만기일 사이여야 합니다."),
    /** 실행 시점 예상 잔액보다 큰 추가상환·대환 금액. */
    SIMULATION_AMOUNT_EXCEEDS_BALANCE(HttpStatus.BAD_REQUEST, "실행 시점의 대출 잔액을 초과하는 금액입니다."),
    /** 보유 대출의 잔여 회차가 계산 상한(360회)을 넘는 경우. 잘린 이자를 총액으로 저장하지 않는다. */
    SIMULATION_PERIOD_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "잔여 상환 회차가 계산 상한을 초과합니다."),
    /** 계산 결과·차이가 원 단위 정수 범위를 넘는 경우. */
    SIMULATION_AMOUNT_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "계산 결과가 허용 범위를 초과합니다."),

    // ---------- Simulation 할부·리볼빙·현금서비스·복합 (S15P21D105-586) ----------
    /**
     * 복합 시나리오에 정규화 결과가 같은 자식이 둘 이상인 경우. 같은 행동을 두 번 더해 효과를 이중 반영하지 않는다.
     * 할부의 수수료율 모드(생략/명시적 0)는 지문에는 들어가지만 이 판정에서는 제외한다.
     */
    SIMULATION_COMBINED_DUPLICATE_SCENARIO(HttpStatus.BAD_REQUEST, "복합 시나리오에 같은 행동이 중복되었습니다."),
    /**
     * 서로 다른 자식이 같은 대상(대출·카드·저축 절대액·소비 범위)을 독립적으로 바꾸려는 경우. 앞 자식의 결과를 다음 자식이
     * 이어받지 않으므로 같은 잔액을 두 번 변경한 합계는 의미가 없다.
     */
    SIMULATION_COMBINED_SCENARIO_CONFLICT(HttpStatus.BAD_REQUEST, "복합 시나리오의 자식이 같은 대상을 중복 변경합니다."),

    // ---------- Pattern (S15P21D105-516 ~ 518) ----------
    /**
     * 임대 기간 안의 진행 중 분석이 있는 경우(명세 "중복 분석 요청 방지").
     */
    PATTERN_ANALYSIS_IN_PROGRESS(HttpStatus.CONFLICT, "패턴 분석이 이미 진행 중입니다."),
    /**
     * 접수가 만료(임대 초과·이후 접수 존재·이미 완료)된 뒤 완료를 시도한 경우. 정상 흐름에서는 발생하지 않는다.
     */
    PATTERN_ANALYSIS_EXPIRED(HttpStatus.CONFLICT, "분석 접수가 만료되어 결과를 저장할 수 없습니다."),
    /**
     * 완료된 분석 결과가 없는 경우(명세 "분석 결과가 존재해야 함").
     */
    PATTERN_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "패턴 분석 결과가 없습니다. 먼저 분석을 실행해 주세요."),
    /**
     * 분석 기간에 거래가 없는 경우(명세 "분석 가능 거래 데이터가 존재해야 함"). 금융망 미연결이면 FINANCE_NOT_CONNECTED를 쓴다.
     */
    PATTERN_INSUFFICIENT_DATA(HttpStatus.CONFLICT, "분석할 금융 데이터가 없습니다. 금융 데이터 연동 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
