package com.ssafy.dodam.global.exception;

/**
 * 도메인 규칙 위반을 공통 응답 형식으로 내보내기 위한 예외.
 * ErrorCode가 HTTP 상태와 사용자 노출 메시지를 함께 들고 있으므로 핸들러는 분기 없이 변환만 한다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
