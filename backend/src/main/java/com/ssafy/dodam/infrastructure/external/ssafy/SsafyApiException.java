package com.ssafy.dodam.infrastructure.external.ssafy;

/**
 * 금융망이 오류 응답(responseCode)을 돌려준 경우. 네트워크 오류는 {@link SsafyUnavailableException}.
 */
public class SsafyApiException extends RuntimeException {

    private final String responseCode;

    public SsafyApiException(String responseCode, String responseMessage) {
        // 금융망 메시지에는 계좌번호 등 요청 데이터가 섞일 수 있어 예외 체인에도 보관하지 않는다.
        super("SSAFY finance API error " + responseCode);
        this.responseCode = responseCode;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public boolean is(SsafyErrorCode code) {
        return code.code().equals(responseCode);
    }
}
