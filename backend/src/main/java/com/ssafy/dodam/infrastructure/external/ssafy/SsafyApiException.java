package com.ssafy.dodam.infrastructure.external.ssafy;

/**
 * 금융망이 오류 응답(responseCode)을 돌려준 경우. 네트워크 오류는 {@link SsafyUnavailableException}.
 */
public class SsafyApiException extends RuntimeException {

    private final String responseCode;

    public SsafyApiException(String responseCode, String responseMessage) {
        super("SSAFY finance API error " + responseCode + ": " + responseMessage);
        this.responseCode = responseCode;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public boolean is(SsafyErrorCode code) {
        return code.code().equals(responseCode);
    }
}
