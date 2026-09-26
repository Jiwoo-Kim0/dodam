package com.ssafy.dodam.domain.minusaccount.service;

import com.ssafy.dodam.domain.minusaccount.dto.request.MinusAccountInterestPaymentRequest;
import com.ssafy.dodam.domain.minusaccount.dto.request.MinusAccountRepaymentRequest;
import com.ssafy.dodam.domain.minusaccount.dto.response.MinusAccountInterestPaymentResponse;
import com.ssafy.dodam.domain.minusaccount.dto.response.MinusAccountRepaymentResponse;
import com.ssafy.dodam.domain.minusaccount.entity.MinusAccount;
import com.ssafy.dodam.domain.minusaccount.entity.MinusAccountOwner;
import com.ssafy.dodam.domain.minusaccount.repository.AccountBalanceWriter;
import com.ssafy.dodam.domain.minusaccount.repository.MinusAccountOwnerRepository;
import com.ssafy.dodam.domain.minusaccount.repository.MinusAccountRepository;
import com.ssafy.dodam.domain.goal.repository.SafeboxAccountRepository;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 마이너스통장 원금 상환과 이자 납부.
 *
 * <p><b>시연용 로컬 상태 전이다.</b> SSAFY 금융망에 실행을 요청하지 않고 우리 DB의
 * `minus_account`와 `account`만 바꾼다. 시연 계좌가 `source_type = 'SEED'`라 금융망에 존재하지
 * 않기 때문이며, 외부 연동 계약이 확정되면 이 서비스 뒤에 어댑터를 붙여 교체한다.
 *
 * <p><b>중복 요청 방지는 아직 구현하지 않았다.</b> 행 잠금으로 동시 요청의 경쟁 조건만 막는다.
 * 재전송 멱등성은 `Idempotency-Key`와 처리 결과 저장이 필요한데 그 계약이 정해지지 않았다.
 * 명세의 "중복 요청 방지"를 충족하지 못한 상태다.
 *
 * <p>거래내역(`transaction`)에도 기록하지 않는다. 거래 원장은 BE-A 소관이고 시스템 생성 거래의
 * 식별자·카테고리 규약이 없어 임의 규칙으로 쓰지 않는다.
 */
@Service
@Transactional
public class MinusAccountCommandService {

    private static final String ACTIVE = "ACTIVE";

    private final MinusAccountOwnerRepository ownerRepository;
    private final MinusAccountRepository minusAccountRepository;
    private final AccountBalanceWriter accountBalanceWriter;
    private final SensitiveNumberHasher sensitiveNumberHasher;
    private final MinusAccountClock clock;
    private final SafeboxAccountRepository safeboxAccounts;

    public MinusAccountCommandService(
            MinusAccountOwnerRepository ownerRepository,
            MinusAccountRepository minusAccountRepository,
            AccountBalanceWriter accountBalanceWriter,
            SensitiveNumberHasher sensitiveNumberHasher,
            MinusAccountClock clock, SafeboxAccountRepository safeboxAccounts
    ) {
        this.ownerRepository = ownerRepository;
        this.minusAccountRepository = minusAccountRepository;
        this.accountBalanceWriter = accountBalanceWriter;
        this.sensitiveNumberHasher = sensitiveNumberHasher;
        this.clock = clock;
        this.safeboxAccounts = safeboxAccounts;
    }

    public MinusAccountRepaymentResponse repay(
            Long userId,
            String accountNo,
            MinusAccountRepaymentRequest request
    ) {
        MinusAccountOwner owner = findActiveOwner(userId, accountNo);
        MinusAccountOwner withdrawal = resolveWithdrawalAccount(userId, request.repayAccountNo());
        MinusAccount detail = lockDetail(userId, owner.getId());

        long amount = request.amount();
        if (amount > detail.getUsedAmount()) {
            throw new BusinessException(ErrorCode.REPAYMENT_AMOUNT_EXCEEDED);
        }
        if (withdrawal.getBalance() < amount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_ACCOUNT_BALANCE);
        }

        LocalDateTime now = clock.now();
        detail.repayPrincipal(amount, now);
        minusAccountRepository.saveAndFlush(detail);

        // ERD 계산 기준에 따라 상위 계좌 잔액을 같은 트랜잭션에서 -used_amount로 맞춘다.
        accountBalanceWriter.updateMinusBalance(userId, owner.getId(), detail.getUsedAmount(), now);
        accountBalanceWriter.withdraw(userId, withdrawal.getId(), amount, now);

        return new MinusAccountRepaymentResponse(
                detail.getAccountId(),
                owner.getAccountNoMasked(),
                amount,
                detail.getLimitAmount(),
                detail.getUsedAmount(),
                detail.getAvailableAmount(),
                detail.getAccruedInterest(),
                withdrawal.getAccountNoMasked(),
                withdrawal.getBalance() - amount,
                now
        );
    }

    public MinusAccountInterestPaymentResponse payInterest(
            Long userId,
            String accountNo,
            MinusAccountInterestPaymentRequest request
    ) {
        MinusAccountOwner owner = findActiveOwner(userId, accountNo);
        MinusAccountOwner withdrawal = resolveWithdrawalAccount(userId, request.paymentAccountNo());
        MinusAccount detail = lockDetail(userId, owner.getId());

        if (detail.getAccruedInterest() == 0) {
            throw new BusinessException(ErrorCode.NO_ACCRUED_INTEREST);
        }

        // amount를 생략하면 잔여 미수이자 전액을 납부한다.
        long amount = request.amount() == null ? detail.getAccruedInterest() : request.amount();
        if (amount > detail.getAccruedInterest()) {
            throw new BusinessException(ErrorCode.REPAYMENT_AMOUNT_EXCEEDED);
        }
        if (withdrawal.getBalance() < amount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_ACCOUNT_BALANCE);
        }

        LocalDateTime now = clock.now();
        detail.payInterest(amount, now);
        minusAccountRepository.saveAndFlush(detail);

        // 이자 납부는 원금을 줄이지 않으므로 마이너스통장 잔액은 그대로 둔다.
        accountBalanceWriter.withdraw(userId, withdrawal.getId(), amount, now);

        return new MinusAccountInterestPaymentResponse(
                detail.getAccountId(),
                owner.getAccountNoMasked(),
                amount,
                detail.getAccruedInterest(),
                detail.getUsedAmount(),
                withdrawal.getAccountNoMasked(),
                withdrawal.getBalance() - amount,
                now
        );
    }

    private MinusAccountOwner findActiveOwner(Long userId, String accountNo) {
        MinusAccountOwner owner = ownerRepository
                .findByUserIdAndAccountNoHashAndAccountType(
                        userId,
                        sensitiveNumberHasher.hash(accountNo),
                        MinusAccountOwner.MINUS
                )
                .orElseThrow(() -> new ResourceNotFoundException("마이너스통장을 찾을 수 없습니다."));

        if (!ACTIVE.equals(owner.getStatus())) {
            throw new BusinessException(ErrorCode.MINUS_ACCOUNT_NOT_ACTIVE);
        }
        return owner;
    }

    /**
     * 출금계좌를 특정한다.
     *
     * <p>`minus_account`에는 출금계좌 컬럼이 없어 저장된 기본값이 없다. 명세에도 기본 규칙이
     * 없으므로 활성 입출금계좌가 정확히 1개일 때만 자동 선택하고 그 외에는 지정을 요구한다.
     * 임의 선택은 잘못된 계좌에서 출금될 수 있어 하지 않는다.
     */
    private MinusAccountOwner resolveWithdrawalAccount(Long userId, String accountNo) {
        if (StringUtils.hasText(accountNo)) {
            MinusAccountOwner account = accountBalanceWriter
                    .findWithdrawalAccountForUpdate(userId, sensitiveNumberHasher.hash(accountNo))
                    .filter(candidate -> ACTIVE.equals(candidate.getStatus()))
                    .orElseThrow(() -> new ResourceNotFoundException("출금계좌를 찾을 수 없습니다."));
            rejectSafeboxDebit(userId, account);
            return account;
        }

        Long dedicatedAccountId = safeboxAccounts.findById(userId)
                .map(ref -> ref.getAccountId()).orElse(null);
        List<MinusAccountOwner> candidates = accountBalanceWriter.findDemandDepositAccounts(userId).stream()
                .filter(candidate -> !candidate.getId().equals(dedicatedAccountId))
                .toList();
        if (candidates.size() != 1) {
            throw new BusinessException(ErrorCode.WITHDRAWAL_ACCOUNT_REQUIRED);
        }
        return candidates.get(0);
    }

    private void rejectSafeboxDebit(Long userId, MinusAccountOwner account) {
        if (safeboxAccounts.findById(userId)
                .map(ref -> ref.getAccountId().equals(account.getId())).orElse(false)) {
            throw new BusinessException(ErrorCode.SAFEBOX_ACCOUNT_DEBIT_NOT_ALLOWED);
        }
    }

    private MinusAccount lockDetail(Long userId, Long accountId) {
        return minusAccountRepository.findForUpdate(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("마이너스통장을 찾을 수 없습니다."));
    }
}
