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

    // ---------- Recommendation (S15P21D105-548) ----------
    /**
     * 아직 완료되지 않은 진단으로 개선안을 만들려는 경우. 진단은 존재하므로 404가 아니라 409다.
     */
    DIAGNOSIS_NOT_COMPLETED(HttpStatus.CONFLICT, "완료된 진단이 아닙니다."),
    /**
     * 요청 goalId가 진단의 goalId와 다른 경우. 다른 목표로 진단한 근거를 이 목표의 개선안에 쓰면 목표기여 계산이 틀어진다.
     */
    RECOMMENDATION_GOAL_MISMATCH(HttpStatus.BAD_REQUEST, "진단의 목표와 다른 목표를 지정할 수 없습니다.");

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
