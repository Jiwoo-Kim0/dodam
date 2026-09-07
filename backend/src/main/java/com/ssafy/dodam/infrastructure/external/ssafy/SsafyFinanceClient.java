package com.ssafy.dodam.infrastructure.external.ssafy;

/**
 * SSAFY 금융망 호출 포트. 도메인 서비스는 이 인터페이스에만 의존하고, 테스트에서는 Mock으로 대체한다.
 */
public interface SsafyFinanceClient {

    /**
     * 2.2.1 사용자 계정 생성.
     *
     * @throws SsafyApiException E4002(이미 존재하는 ID) 등 금융망 오류 응답
     * @throws SsafyUnavailableException 네트워크 오류
     */
    SsafyMember createMember(String email);

    /**
     * 2.2.2 사용자 계정 조회. 우리 앱(API Key)에 등록된 사용자만 조회된다.
     *
     * @throws SsafyApiException E4003(존재하지 않는 ID) 등 금융망 오류 응답
     * @throws SsafyUnavailableException 네트워크 오류
     */
    SsafyMember searchMember(String email);
}
