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
 * 회원가입 시 SSAFY 금융망 사용자 계정을 만들고 userKey를 확보한다.
 *
 * 정책
 * - 생성 성공 → CONNECTED
 * - E4002(이미 존재) → 조회로 userKey 회수. 조회도 E4003이면 다른 서비스가 선점한 이메일이므로 가입 거절(409)
 * - 그 외 금융망 오류·네트워크 장애 → 가입은 진행하고 연결 상태만 FAILED (금융망 장애가 회원가입을 막지 않게)
 *
 * 이 메서드는 DB 트랜잭션 바깥에서 호출한다 — 외부 API 응답을 기다리며 커넥션을 붙잡지 않기 위해서다.
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
            return Optional.of(ssafyFinanceClient.createMember(email).userKey());
        } catch (SsafyApiException exception) {
            if (exception.is(SsafyErrorCode.MEMBER_ALREADY_EXISTS)) {
                return findExistingUserKey(email);
            }
            log.warn("SSAFY member creation failed for masked email {}: {}",
                    FieldCryptor.maskEmail(email), exception.getMessage());
            return Optional.empty();
        } catch (SsafyUnavailableException exception) {
            log.warn("SSAFY finance API unavailable during signup: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> findExistingUserKey(String email) {
        try {
            SsafyMember member = ssafyFinanceClient.searchMember(email);
            return Optional.of(member.userKey());
        } catch (SsafyApiException exception) {
            if (exception.is(SsafyErrorCode.MEMBER_NOT_FOUND)) {
                throw new BusinessException(ErrorCode.FINANCE_EMAIL_ALREADY_REGISTERED);
            }
            log.warn("SSAFY member search failed for masked email {}: {}",
                    FieldCryptor.maskEmail(email), exception.getMessage());
            return Optional.empty();
        } catch (SsafyUnavailableException exception) {
            log.warn("SSAFY finance API unavailable during member search: {}", exception.getMessage());
            return Optional.empty();
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
