package com.ssafy.dodam.domain.finance.service;

import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyApiException;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyUnavailableException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 사용자가 직접 요청한 금융망 호출의 실패를 공통 오류 코드로 바꾼다.
 *
 * - 금융망 오류 응답(responseCode) → 502 FINANCE_API_ERROR. 코드·메시지는 로그에만 남기고 클라이언트에는 노출하지 않는다.
 * - 네트워크 장애·해석 불가 → 503 FINANCE_API_UNAVAILABLE.
 *
 * 회원가입처럼 실패를 삼키고 진행해야 하는 흐름은 이 헬퍼를 쓰지 않는다 ({@link FinanceMemberService#registerOrFindUserKey}).
 */
final class SsafyCalls {

    private static final Logger log = LoggerFactory.getLogger(SsafyCalls.class);

    private SsafyCalls() {
    }

    static <T> T call(String operation, Supplier<T> call) {
        try {
            return call.get();
        } catch (SsafyApiException exception) {
            log.warn("SSAFY finance API error during {}: {}", operation, exception.getMessage());
            throw new BusinessException(ErrorCode.FINANCE_API_ERROR);
        } catch (SsafyUnavailableException exception) {
            log.warn("SSAFY finance API unavailable during {}: {}", operation, exception.getMessage());
            throw new BusinessException(ErrorCode.FINANCE_API_UNAVAILABLE);
        }
    }
}
