package com.ssafy.dodam.domain.finance.service;

import com.ssafy.dodam.domain.finance.dto.FinanceConnectionResponse;
import com.ssafy.dodam.domain.finance.dto.FinanceMemberResponse;
import com.ssafy.dodam.domain.finance.entity.FinanceConnection;
import com.ssafy.dodam.domain.finance.repository.FinanceConnectionRepository;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.global.crypto.FieldCryptor;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyFinanceClient;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyMember;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * API 명세 4. Finance Data — 금융망 연결·회원 관리 (S15P21D105-498).
 * 모든 메서드는 인증된 본인(userId)의 연결만 다룬다.
 */
@Service
public class FinanceConnectionService {

    private final UserRepository userRepository;
    private final FinanceConnectionRepository financeConnectionRepository;
    private final FinanceMemberService financeMemberService;
    private final SsafyFinanceClient ssafyFinanceClient;
    private final FieldCryptor fieldCryptor;
    private final TransactionTemplate transactionTemplate;

    public FinanceConnectionService(
            UserRepository userRepository,
            FinanceConnectionRepository financeConnectionRepository,
            FinanceMemberService financeMemberService,
            SsafyFinanceClient ssafyFinanceClient,
            FieldCryptor fieldCryptor,
            TransactionTemplate transactionTemplate
    ) {
        this.userRepository = userRepository;
        this.financeConnectionRepository = financeConnectionRepository;
        this.financeMemberService = financeMemberService;
        this.ssafyFinanceClient = ssafyFinanceClient;
        this.fieldCryptor = fieldCryptor;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 연결 행이 아직 없으면(Seed 사용자 등) PENDING으로 본다 — UserResponse.financeConnectionStatus와 같은 규칙.
     */
    @Transactional(readOnly = true)
    public FinanceConnectionResponse getConnection(Long userId) {
        findActiveUser(userId);
        return financeConnectionRepository.findByUserId(userId)
                .map(FinanceConnectionResponse::from)
                .orElseGet(FinanceConnectionResponse::pending);
    }

    /**
     * 금융망 사용자 등록(연결). 회원가입 때 FAILED로 남았거나 해제한 연결을 다시 잇는 데 쓴다.
     *
     * 1. 이미 CONNECTED면 409 — 같은 요청을 반복해도 금융망을 다시 호출하지 않는다.
     * 2. 금융망 호출은 트랜잭션 밖에서 먼저 한다 (회원가입과 같은 이유).
     * 3. 사용자 행을 잠근 채 상태를 다시 확인하고 CONNECTED로 바꾼다. 행이 없으면 새로 만들며,
     *    동시에 두 요청이 생성까지 왔다면 UNIQUE(user_id)로 걸러 409를 돌려준다.
     */
    public FinanceConnectionResponse connect(Long userId) {
        User user = findActiveUser(userId);
        financeConnectionRepository.findByUserId(userId)
                .filter(FinanceConnection::isConnected)
                .ifPresent(connection -> {
                    throw new BusinessException(ErrorCode.FINANCE_ALREADY_CONNECTED);
                });

        String email = user.getEmail();
        String userKey = financeMemberService.requireUserKey(email);

        return transactionTemplate.execute(status -> {
            FinanceConnection connection = financeConnectionRepository.findByUserIdForUpdate(userId).orElse(null);
            if (connection == null) {
                try {
                    connection = financeConnectionRepository.saveAndFlush(
                            financeMemberService.buildConnection(userId, email, Optional.of(userKey))
                    );
                } catch (DataIntegrityViolationException exception) {
                    throw new BusinessException(ErrorCode.FINANCE_ALREADY_CONNECTED);
                }
            } else {
                if (connection.isConnected()) {
                    throw new BusinessException(ErrorCode.FINANCE_ALREADY_CONNECTED);
                }
                connection.connect(fieldCryptor.encrypt(userKey), fieldCryptor.hash(userKey), LocalDateTime.now());
            }
            return FinanceConnectionResponse.from(connection);
        });
    }

    /**
     * 금융망 사용자 조회 (2.2.2). userKey는 서버에만 두고 응답에는 절대 싣지 않는다.
     */
    public FinanceMemberResponse getMember(Long userId) {
        findActiveUser(userId);
        FinanceConnection connection = requireConnected(userId);
        String email = memberEmailOf(connection, userId);
        SsafyMember member = SsafyCalls.call("member search", () -> ssafyFinanceClient.searchMember(email));
        return FinanceMemberResponse.of(member, connection);
    }

    /**
     * 연동 해제. 금융망 계정 자체는 삭제 API가 없어 그대로 두고, 우리 쪽 연결만 DISCONNECTED로 바꾼다.
     * userKey 암호문은 재연결 시 덮어쓰므로 지우지 않는다.
     */
    @Transactional
    public FinanceConnectionResponse disconnect(Long userId) {
        findActiveUser(userId);
        FinanceConnection connection = financeConnectionRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FINANCE_CONNECTION_NOT_FOUND));
        if (connection.isDisconnected()) {
            throw new BusinessException(ErrorCode.FINANCE_NOT_CONNECTED);
        }
        connection.disconnect(LocalDateTime.now());
        return FinanceConnectionResponse.from(connection);
    }

    /**
     * 금융망 호출이 필요한 다른 Finance Data 기능(동기화·신용등급)이 공유하는 전제 조건.
     *
     * @throws BusinessException FINANCE_NOT_CONNECTED — 행이 없거나, CONNECTED가 아니거나, userKey가 없는 경우
     */
    @Transactional(readOnly = true)
    public FinanceConnection requireConnected(Long userId) {
        return financeConnectionRepository.findByUserId(userId)
                .filter(connection -> connection.isConnected() && connection.hasUserKey())
                .orElseThrow(() -> new BusinessException(ErrorCode.FINANCE_NOT_CONNECTED));
    }

    public String userKeyOf(FinanceConnection connection) {
        return fieldCryptor.decrypt(connection.getExternalUserKeyEncrypted());
    }

    private String memberEmailOf(FinanceConnection connection, Long userId) {
        if (connection.getMemberEmailEncrypted() != null) {
            return fieldCryptor.decrypt(connection.getMemberEmailEncrypted());
        }
        return findActiveUser(userId).getEmail();
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_EXPIRED));
    }
}
