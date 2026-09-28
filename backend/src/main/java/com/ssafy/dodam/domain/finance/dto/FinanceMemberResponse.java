package com.ssafy.dodam.domain.finance.dto;

import com.ssafy.dodam.domain.finance.entity.FinanceConnection;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyMember;
import java.time.LocalDateTime;

/**
 * 금융망 사용자 계정 조회(2.2.2) 결과. userKey는 서버 관리 항목이므로 내보내지 않는다.
 *
 * @param userId          금융망 사용자 ID (= 가입 이메일)
 * @param institutionCode 금융망 기관코드 (교육용 금융망은 항상 00100)
 */
public record FinanceMemberResponse(
        String userId,
        String userName,
        String institutionCode,
        LocalDateTime connectedAt
) {

    public static FinanceMemberResponse of(SsafyMember member, FinanceConnection connection) {
        return new FinanceMemberResponse(
                member.userId(),
                member.userName(),
                member.institutionCode(),
                connection.getConnectedAt()
        );
    }
}
