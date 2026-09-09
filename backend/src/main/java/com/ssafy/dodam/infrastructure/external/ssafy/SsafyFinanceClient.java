package com.ssafy.dodam.infrastructure.external.ssafy;

import java.util.List;

/**
 * SSAFY 금융망 호출 포트. 도메인 서비스는 이 인터페이스에만 의존하고, 테스트에서는 Mock으로 대체한다.
 *
 * 사용자 단위 API는 userKey 원문을 받는다. 구현은 userKey를 로그에 남기지 않는다.
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

    /**
     * 내 신용등급 조회 (대출 > inquireMyCreditRating).
     *
     * @throws SsafyApiException 금융망 오류 응답
     * @throws SsafyUnavailableException 네트워크 오류·응답 해석 불가
     */
    SsafyCreditRating inquireMyCreditRating(String userKey);

    /**
     * 신용등급 기준 조회 (대출 > inquireCreditRatingList). 등급별 요구 자산·비율 표.
     *
     * @throws SsafyApiException 금융망 오류 응답
     * @throws SsafyUnavailableException 네트워크 오류·응답 해석 불가
     */
    List<SsafyCreditRatingCriterion> inquireCreditRatingList(String userKey);
}
