package com.ssafy.dodam.infrastructure.external.ssafy;

/**
 * 이 프로젝트가 분기 처리하거나 로그에서 식별하는 금융망 응답 코드만 정의한다.
 * E**** 는 사용자 계정 API(2.2), H**** 는 공통 Header 검증(2.3.1) 오류다. 정상 처리는 {@link #SUCCESS}.
 */
public enum SsafyErrorCode {
    SUCCESS("H0000"),
    MEMBER_ALREADY_EXISTS("E4002"),
    MEMBER_NOT_FOUND("E4003"),
    INVALID_API_KEY("E4004"),
    /** 기관거래고유번호가 중복된 값입니다. */
    DUPLICATE_TRANSACTION_NO("H1007"),
    /** API_KEY가 유효하지 않습니다 (거래 API). */
    INVALID_HEADER_API_KEY("H1008"),
    /** USER_KEY가 유효하지 않습니다. */
    INVALID_USER_KEY("H1009");

    private final String code;

    SsafyErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
