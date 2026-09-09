package com.ssafy.dodam.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
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
    FINANCE_API_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "금융망에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.");

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
