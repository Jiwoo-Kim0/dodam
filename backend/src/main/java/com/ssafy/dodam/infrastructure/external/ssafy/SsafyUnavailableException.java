package com.ssafy.dodam.infrastructure.external.ssafy;

/**
 * 금융망에 도달하지 못했거나(타임아웃, 연결 실패) 응답을 해석할 수 없는 경우.
 */
public class SsafyUnavailableException extends RuntimeException {

    public SsafyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
