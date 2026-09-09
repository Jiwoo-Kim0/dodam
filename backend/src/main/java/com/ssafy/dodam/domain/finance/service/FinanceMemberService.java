package com.ssafy.dodam.domain.finance.service;

import com.ssafy.dodam.domain.finance.entity.FinanceConnection;
import com.ssafy.dodam.global.crypto.FieldCryptor;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyApiException;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyErrorCode;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyFinanceClient;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyMember;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyUnavailableException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * SSAFY 금융망 사용자 계정을 만들고 userKey를 확보한다 (API 문서 2.2.1 생성 / 2.2.2 조회).
 *
 * 정책
 * - 생성 성공 → userKey
 * - E4002(이미 존재) → 조회로 userKey 회수. 조회도 E4003이면 다른 서비스가 선점한 이메일이므로 409
 * - 그 외 금융망 오류·네트워크 장애 → 호출 목적에 따라 다르게 처리한다
 *   · 회원가입({@link #registerOrFindUserKey}): 가입은 진행하고 연결 상태만 FAILED (금융망 장애가 회원가입을 막지 않게)
 *   · 명시적 연결 요청({@link #requireUserKey}): 502/503으로 실패를 그대로 알린다
 *
 * 두 메서드 모두 DB 트랜잭션 바깥에서 호출한다 — 외부 API 응답을 기다리며 커넥션을 붙잡지 않기 위해서다.
 */
@Service
public class FinanceMemberService {

    private static final Logger log = LoggerFactory.getLogger(FinanceMemberService.class);

    private final SsafyFinanceClient ssafyFinanceClient;
    private final FieldCryptor fieldCryptor;

    public FinanceMemberService(SsafyFinanceClient ssafyFinanceClient, FieldCryptor fieldCryptor) {
        this.ssafyFinanceClient = ssafyFinanceClient;
        this.fieldCryptor = fieldCryptor;
    }

    /**
     * @return 확보한 userKey. 금융망 장애로 확보하지 못했으면 empty
     * @throws BusinessException FINANCE_EMAIL_ALREADY_REGISTERED — 다른 서비스에 등록된 이메일
     */
    public Optional<String> registerOrFindUserKey(String email) {
        try {
            return Optional.of(requireUserKey(email));
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.FINANCE_EMAIL_ALREADY_REGISTERED) {
                throw exception;
            }
            return Optional.empty();
        }
    }

    /**
     * userKey를 반드시 확보해야 하는 흐름(POST /finance-data/connection)용.
     *
     * @throws BusinessException FINANCE_EMAIL_ALREADY_REGISTERED(409), FINANCE_API_ERROR(502), FINANCE_API_UNAVAILABLE(503)
     */
    public String requireUserKey(String email) {
        try {
            return ssafyFinanceClient.createMember(email).userKey();
        } catch (SsafyApiException exception) {
            if (exception.is(SsafyErrorCode.MEMBER_ALREADY_EXISTS)) {
                return findExistingUserKey(email);
            }
            log.warn("SSAFY member creation failed for masked email {}: {}",
                    FieldCryptor.maskEmail(email), exception.getMessage());
            throw new BusinessException(ErrorCode.FINANCE_API_ERROR);
        } catch (SsafyUnavailableException exception) {
            log.warn("SSAFY finance API unavailable during member creation: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.FINANCE_API_UNAVAILABLE);
        }
    }

    private String findExistingUserKey(String email) {
        try {
            SsafyMember member = ssafyFinanceClient.searchMember(email);
            return member.userKey();
        } catch (SsafyApiException exception) {
            if (exception.is(SsafyErrorCode.MEMBER_NOT_FOUND)) {
                throw new BusinessException(ErrorCode.FINANCE_EMAIL_ALREADY_REGISTERED);
            }
            log.warn("SSAFY member search failed for masked email {}: {}",
                    FieldCryptor.maskEmail(email), exception.getMessage());
            throw new BusinessException(ErrorCode.FINANCE_API_ERROR);
        } catch (SsafyUnavailableException exception) {
            log.warn("SSAFY finance API unavailable during member search: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.FINANCE_API_UNAVAILABLE);
        }
    }

    /**
     * userKey 유무에 따라 CONNECTED 또는 FAILED 연결 행을 만든다 (저장은 호출자 트랜잭션에서).
     */
    public FinanceConnection buildConnection(Long userId, String email, Optional<String> userKey) {
        byte[] emailEncrypted = fieldCryptor.encrypt(email);
        String emailHash = fieldCryptor.hash(email);
        String emailMasked = FieldCryptor.maskEmail(email);

        return userKey
                .map(key -> FinanceConnection.connected(
                        userId,
                        emailEncrypted,
                        emailHash,
                        emailMasked,
                        fieldCryptor.encrypt(key),
                        fieldCryptor.hash(key),
                        LocalDateTime.now()
                ))
                .orElseGet(() -> FinanceConnection.failed(userId, emailEncrypted, emailHash, emailMasked));
    }
}
