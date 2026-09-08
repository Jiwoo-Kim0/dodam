package com.ssafy.dodam.infrastructure.external.ssafy;

/**
 * 이 프로젝트가 분기 처리하는 금융망 응답 코드만 정의한다 (API 문서 2.2 사용자 로그인 에러코드).
 */
public enum SsafyErrorCode {
    MEMBER_ALREADY_EXISTS("E4002"),
    MEMBER_NOT_FOUND("E4003"),
    INVALID_API_KEY("E4004");

    private final String code;

    SsafyErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
