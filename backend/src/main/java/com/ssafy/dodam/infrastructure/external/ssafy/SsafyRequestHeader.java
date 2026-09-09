package com.ssafy.dodam.infrastructure.external.ssafy;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * SSAFY 금융망 거래 API 공통 요청 Header (API 문서 2.3.1 「공통 HEADER API」).
 * 필드명은 금융망이 요구하는 JSON 키와 정확히 같아야 하므로 바꾸지 않는다.
 *
 * @param institutionTransactionUniqueNo 기관거래고유번호 20자리 — YYYYMMDD(8) + HHMMSS(6) + 일련번호(6). 호출마다 새로 만든다 (중복이면 H1007)
 * @param apiServiceCode                 apiName과 동일
 * @param userKey                        사용자 단위 API에만 넣는다. 앱 단위 API(예: 신용등급 기준 조회)는 문서에 "userKey 제외"로 명시되어 있어
 *                                       null이면 JSON에서 키 자체를 뺀다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SsafyRequestHeader(
        String apiName,
        String transmissionDate,
        String transmissionTime,
        String institutionCode,
        String fintechAppNo,
        String apiServiceCode,
        String institutionTransactionUniqueNo,
        String apiKey,
        String userKey
) {
}
