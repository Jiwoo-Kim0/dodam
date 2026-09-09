package com.ssafy.dodam.infrastructure.external.ssafy;

/**
 * SSAFY 금융망 거래 API 공통 요청 Header (API 문서 「공통 Header」).
 * 필드명은 금융망이 요구하는 JSON 키와 정확히 같아야 하므로 바꾸지 않는다.
 *
 * @param institutionTransactionUniqueNo 기관거래고유번호 20자리 — YYYYMMDD(8) + HHMMSS(6) + 일련번호(6). 호출마다 새로 만든다
 * @param apiServiceCode                 apiName과 동일
 * @param userKey                        사용자 계정 생성 시 받은 키. 사용자 단위 API에만 필요하다
 */
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
