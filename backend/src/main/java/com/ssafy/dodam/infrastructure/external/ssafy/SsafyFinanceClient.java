package com.ssafy.dodam.infrastructure.external.ssafy;

import java.time.LocalDate;
import java.util.List;

/**
 * SSAFY 금융망 호출 포트. 도메인 서비스는 이 인터페이스에만 의존하고, 테스트에서는 Mock으로 대체한다.
 *
 * 사용자 단위 API는 userKey 원문을 받는다. 구현은 userKey를 로그에 남기지 않는다.
 */
public interface SsafyFinanceClient {

    List<SsafyDemandDepositProduct> inquireDemandDepositList();

    SsafyDemandDepositProduct createDemandDeposit(String bankCode, String name, String description);

    SsafyCreatedDemandDepositAccount createDemandDepositAccount(String userKey, String typeNo);

    SsafyDepositResult depositToAccount(String userKey, String accountNo, long amount, String summary);

    List<SsafyTransferEntry> transfer(String userKey, String withdrawalNo, String depositNo,
            long amount, String withdrawalSummary, String depositSummary);

    SsafyAccountBalance inquireAccountBalance(String userKey, String accountNo);

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
     * 2.4 수시입출금 계좌 목록 조회 (inquireDemandDepositAccountList). 사용자 단위 API.
     * 계좌가 없으면 빈 목록을 돌려준다.
     *
     * @throws SsafyApiException H1009(USER_KEY 무효) 등 금융망 오류 응답
     * @throws SsafyUnavailableException 네트워크 오류·응답 해석 불가
     */
    List<SsafyDemandDepositAccount> inquireDemandDepositAccountList(String userKey);

    /**
     * 2.4 계좌 거래내역 조회 (inquireTransactionHistoryList). 사용자 단위 API. 입금·출금 모두, 최신순.
     * 거래가 없으면 빈 목록을 돌려준다.
     *
     * @param accountNo 계좌번호 원문. 구현은 로그에 남기지 않는다.
     * @param startDate 조회 시작일(포함)
     * @param endDate   조회 종료일(포함)
     * @throws SsafyApiException A1003(계좌 없음), H1009(USER_KEY 무효) 등 금융망 오류 응답
     * @throws SsafyUnavailableException 네트워크 오류·응답 해석 불가
     */
    SsafyTransactionHistory inquireTransactionHistoryList(
            String userKey,
            String accountNo,
            LocalDate startDate,
            LocalDate endDate
    );

    /**
     * 2.7.4 내 신용등급 조회 (inquireMyCreditRating). 사용자 단위 API.
     *
     * @throws SsafyApiException H1009(USER_KEY 무효) 등 금융망 오류 응답
     * @throws SsafyUnavailableException 네트워크 오류·응답 해석 불가
     */
    SsafyCreditRating inquireMyCreditRating(String userKey);

    /**
     * 2.7.1 신용등급 기준 조회 (inquireAssetBasedCreditRatingList). 앱 단위 API로 userKey를 보내지 않는다.
     *
     * @throws SsafyApiException 금융망 오류 응답
     * @throws SsafyUnavailableException 네트워크 오류·응답 해석 불가
     */
    List<SsafyCreditRatingCriterion> inquireAssetBasedCreditRatingList();
}
